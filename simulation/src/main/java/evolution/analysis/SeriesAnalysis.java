package evolution.analysis;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.ToDoubleFunction;

/** Validates saved CSV and measures each seed once. It has no dependency on JavaFX or simulation state. */
public final class SeriesAnalysis {
    private SeriesAnalysis() {}

    public record Generation(int number, int living, int ticks, double cooperation, double energy,
                             long matches, long resourceEnergy, long gameEnergy) {}
    public record Run(String scenario, int repetition, long seed, int horizon, int population,
                      boolean completed, Map<Integer, Generation> generations, Map<String, String> metadata) {
        public Run { generations = Map.copyOf(generations); metadata = Map.copyOf(metadata); }
        public double endSurvival() { return completed ? (double) generations.get(horizon).living() / population : 0; }
        public double endCooperation() { return completed ? generations.get(horizon).cooperation() : Double.NaN; }
        public double endEnergy() { return completed ? generations.get(horizon).energy() : Double.NaN; }
    }
    public record Trajectory(int generation, BootstrapStatistics.Estimate survival,
                             BootstrapStatistics.Estimate cooperation, BootstrapStatistics.Estimate energy) {}
    public record GenotypePoint(int generation, int n, Map<String, Double> frequencies) {
        public GenotypePoint { frequencies = Map.copyOf(frequencies); }
    }
    public record Scenario(String id, List<Run> runs, BootstrapStatistics.Estimate completion,
                           BootstrapStatistics.Estimate survival, BootstrapStatistics.Estimate cooperation,
                           BootstrapStatistics.Estimate energy, List<Trajectory> trajectory,
                           Map<String, Double> genotypeFrequencies, List<GenotypePoint> genotypeTrajectory) {
        public Scenario {
            runs = List.copyOf(runs); trajectory = List.copyOf(trajectory);
            genotypeFrequencies = Map.copyOf(genotypeFrequencies); genotypeTrajectory = List.copyOf(genotypeTrajectory);
        }
    }
    public record Effect(String scenario, BootstrapStatistics.Estimate survival, BootstrapStatistics.Estimate cooperation) {}
    public record Report(Path source, int horizon, List<Scenario> scenarios, List<Effect> effects) {
        public Report { scenarios = List.copyOf(scenarios); effects = List.copyOf(effects); }
    }
    private record Key(String scenario, int repetition) {}
    private record GenerationKey(Key run, int generation) {}

    public static Report read(Path directory) throws IOException {
        var runs = CsvTable.read(directory.resolve("runs.csv"));
        var generations = CsvTable.read(directory.resolve("generations.csv"));
        var genotypes = CsvTable.read(directory.resolve("genotype-frequencies.csv"));
        runs.require("scenario_id", "repetition", "seed", "status", "completed_generations", "maximum_generations", "population_size", "ticks_per_generation");
        generations.require("scenario_id", "repetition", "seed", "generation", "living_agents", "ticks", "cooperation_rate",
                "average_energy_hundredths", "matches", "resource_energy_hundredths", "game_energy_hundredths");
        genotypes.require("scenario_id", "repetition", "seed", "generation", "genotype", "count", "frequency");
        if (runs.rows().isEmpty()) throw new IOException("No runs to analyze");
        var metadata = new LinkedHashMap<Key, Map<String, String>>();
        var histories = new HashMap<Key, Map<Integer, Generation>>();
        int horizon = integer(runs.rows().getFirst(), "maximum_generations");
        for (var row : runs.rows()) {
            Key key = key(row);
            if (metadata.putIfAbsent(key, row) != null) throw new IOException("Duplicate run: " + key);
            if (integer(row, "maximum_generations") != horizon || horizon < 1 || integer(row, "population_size") < 1) {
                throw new IOException("Runs must have a common positive generation horizon and positive populations");
            }
            if (!List.of("completed", "extinct").contains(row.get("status"))) throw new IOException("Unknown run status: " + key);
            if (row.containsKey("pairing_mode") && !List.of("local", "global").contains(row.get("pairing_mode"))) {
                throw new IOException("Unknown pairing mode: " + key);
            }
            histories.put(key, new TreeMap<>());
        }
        for (var row : generations.rows()) {
            Key key = key(row);
            var meta = metadata.get(key);
            if (meta == null || number(row, "seed") != number(meta, "seed")) throw new IOException("Generation has no matching run: " + key);
            var generation = new Generation(integer(row, "generation"), integer(row, "living_agents"), integer(row, "ticks"),
                    decimal(row, "cooperation_rate"), decimal(row, "average_energy_hundredths") / 100,
                    number(row, "matches"), number(row, "resource_energy_hundredths"), number(row, "game_energy_hundredths"));
            if (generation.living() < 0 || generation.living() > integer(meta, "population_size") || generation.ticks() < 1
                    || generation.ticks() > integer(meta, "ticks_per_generation")
                    || (generation.living() > 0 && generation.ticks() != integer(meta, "ticks_per_generation"))
                    || generation.matches() < 0 || generation.resourceEnergy() < 0 || generation.gameEnergy() < 0
                    || (generation.matches() > 0 ? !Double.isFinite(generation.cooperation()) : !Double.isNaN(generation.cooperation()))
                    || generation.cooperation() < 0 || generation.cooperation() > 1
                    || (generation.living() > 0 && (!Double.isFinite(generation.energy()) || generation.energy() <= 0))
                    || (generation.living() == 0 && !Double.isNaN(generation.energy()))) throw new IOException("Invalid generation metrics: " + key);
            if (histories.get(key).putIfAbsent(generation.number(), generation) != null) throw new IOException("Duplicate generation: " + key);
        }
        var frequencies = new HashMap<GenerationKey, Map<String, Double>>();
        var counts = new HashMap<GenerationKey, Long>();
        for (var row : genotypes.rows()) {
            Key runKey = key(row);
            var history = histories.get(runKey);
            int generation = integer(row, "generation");
            if (history == null || !history.containsKey(generation)
                    || number(row, "seed") != number(metadata.get(runKey), "seed")) throw new IOException("Genotype has no matching generation");
            var key = new GenerationKey(runKey, generation);
            String genotype = row.get("genotype");
            int count = integer(row, "count");
            int living = history.get(generation).living();
            double frequency = decimal(row, "frequency");
            if (!genotype.matches("[CD]{5}") || count < 0 || count > living
                    || (living == 0 ? !Double.isNaN(frequency) : !Double.isFinite(frequency)
                    || Math.abs(frequency - (double) count / living) > 1e-10)) throw new IOException("Invalid genotype frequency");
            if (frequencies.computeIfAbsent(key, ignored -> new HashMap<>()).putIfAbsent(genotype, frequency) != null) {
                throw new IOException("Duplicate genotype record");
            }
            counts.merge(key, (long) count, Long::sum);
        }
        var grouped = new LinkedHashMap<String, List<Run>>();
        for (var entry : metadata.entrySet()) {
            var key = entry.getKey();
            var row = entry.getValue();
            var history = histories.get(key);
            int recorded = integer(row, "completed_generations");
            if (recorded < 1 || recorded > horizon || history.size() != recorded) throw new IOException("Incomplete run history: " + key);
            for (int generation = 1; generation <= recorded; generation++) {
                var g = history.get(generation);
                var gkey = new GenerationKey(key, generation);
                if (g == null || frequencies.getOrDefault(gkey, Map.of()).size() != 32 || counts.get(gkey) != g.living()) {
                    throw new IOException("Incomplete generation/genotype data: " + gkey);
                }
                if (generation < recorded && g.living() == 0) throw new IOException("Run continues after extinction");
            }
            boolean completed = row.get("status").equals("completed");
            if (completed ? recorded != horizon || history.get(recorded).living() == 0 : history.get(recorded).living() != 0) {
                throw new IOException("Status disagrees with terminal generation: " + key);
            }
            grouped.computeIfAbsent(key.scenario(), ignored -> new ArrayList<>()).add(new Run(key.scenario(), key.repetition(),
                    number(row, "seed"), horizon, integer(row, "population_size"), completed, history, row));
        }
        var scenarios = new ArrayList<Scenario>();
        for (var entry : grouped.entrySet()) {
            var list = entry.getValue();
            list.sort(Comparator.comparingLong(Run::seed));
            var seeds = new HashSet<Long>();
            for (var run : list) {
                if (!seeds.add(run.seed())) throw new IOException("Repeated seed within scenario: " + entry.getKey());
                for (String column : List.of("side_length", "population_size", "maximum_generations", "ticks_per_generation", "rounds_per_match",
                        "initial_energy_hundredths", "resource_energy_hundredths", "living_cost_hundredths", "resource_spawn_probability",
                        "game_payoff_weight_hundredths", "crossover_probability", "mutation_probability", "tournament_size", "pairing_mode",
                        "model_version", "random_generator", "java_version")) {
                    if (!run.metadata().getOrDefault(column, "").equals(list.getFirst().metadata().getOrDefault(column, ""))) {
                        throw new IOException("Configuration/environment changes within scenario: " + entry.getKey());
                    }
                }
            }
            var trajectory = new ArrayList<Trajectory>();
            var genotypeTrajectory = new ArrayList<GenotypePoint>();
            for (int generation = 1; generation <= horizon; generation++) {
                final int g = generation;
                var livingRuns = list.stream().filter(run -> run.generations().containsKey(g) && run.generations().get(g).living() > 0).toList();
                var genotypeMeans = new TreeMap<String, Double>();
                for (var run : livingRuns) {
                    frequencies.get(new GenerationKey(new Key(run.scenario(), run.repetition()), g))
                            .forEach((gene, frequency) -> genotypeMeans.merge(gene, frequency / livingRuns.size(), Double::sum));
                }
                genotypeTrajectory.add(new GenotypePoint(g, livingRuns.size(), genotypeMeans));
                trajectory.add(new Trajectory(g,
                        estimate(list, run -> run.generations().containsKey(g) ? (double) run.generations().get(g).living() / run.population() : 0, entry.getKey()+g+"S"),
                        estimate(list, run -> run.generations().containsKey(g) && run.generations().get(g).living() > 0 ? run.generations().get(g).cooperation() : Double.NaN, entry.getKey()+g+"C"),
                        estimate(list, run -> run.generations().containsKey(g) ? run.generations().get(g).energy() : Double.NaN, entry.getKey()+g+"E")));
            }
            var means = new TreeMap<String, Double>();
            long completed = list.stream().filter(Run::completed).count();
            if (completed > 0) {
                for (var run : list) if (run.completed()) {
                    frequencies.get(new GenerationKey(new Key(run.scenario(), run.repetition()), horizon))
                            .forEach((gene, frequency) -> means.merge(gene, frequency / completed, Double::sum));
                }
            }
            scenarios.add(new Scenario(entry.getKey(), list, BootstrapStatistics.probability((int) completed, list.size()),
                    estimate(list, Run::endSurvival, entry.getKey()+"S"), estimate(list, Run::endCooperation, entry.getKey()+"C"),
                    estimate(list, Run::endEnergy, entry.getKey()+"E"), trajectory, means, genotypeTrajectory));
        }
        var effects = new ArrayList<Effect>();
        var base = grouped.get("baseline");
        if (base != null) {
            var bySeed = new HashMap<Long, Run>();
            base.forEach(run -> bySeed.put(run.seed(), run));
            for (var scenario : scenarios) if (!scenario.id().equals("baseline")) {
                var pairs = scenario.runs().stream().filter(run -> bySeed.containsKey(run.seed())).toList();
                effects.add(new Effect(scenario.id(), estimate(pairs, run -> run.endSurvival() - bySeed.get(run.seed()).endSurvival(), scenario.id()+"pairedS"),
                        estimate(pairs, run -> run.endCooperation() - bySeed.get(run.seed()).endCooperation(), scenario.id()+"pairedC")));
            }
        }
        return new Report(directory.toAbsolutePath().normalize(), horizon, scenarios, effects);
    }

    private static BootstrapStatistics.Estimate estimate(List<Run> runs, ToDoubleFunction<Run> value, String key) {
        return BootstrapStatistics.mean(runs.stream().mapToDouble(value).toArray(), key);
    }
    private static Key key(Map<String, String> row) throws IOException {
        String id = row.get("scenario_id");
        int repetition = integer(row, "repetition");
        if (id.isBlank() || repetition < 1) throw new IOException("Invalid run identity");
        return new Key(id, repetition);
    }
    private static long number(Map<String, String> row, String name) throws IOException {
        try { return Long.parseLong(row.get(name)); }
        catch (RuntimeException exception) { throw new IOException("Invalid integer in column " + name, exception); }
    }
    private static int integer(Map<String, String> row, String name) throws IOException {
        long value = number(row, name);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) throw new IOException("Integer out of range: " + name);
        return (int) value;
    }
    private static double decimal(Map<String, String> row, String name) throws IOException {
        if (row.get(name).isEmpty()) return Double.NaN;
        try {
            double value = Double.parseDouble(row.get(name));
            if (!Double.isFinite(value)) throw new NumberFormatException("Nonfinite value");
            return value;
        } catch (RuntimeException exception) { throw new IOException("Invalid number in column " + name, exception); }
    }
}
