package evolution.simulation;

import java.io.IOException;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.HashSet;
import java.util.stream.Collectors;

public final class CsvResultsWriter {

    private CsvResultsWriter() {
    }

    public static void write(Path outputDirectory, List<SimulationRunResult> runs)
            throws IOException {
        List<ExperimentRun> experiments = new ArrayList<>();
        for (int index = 0; index < runs.size(); index++) {
            experiments.add(new ExperimentRun("baseline", index + 1, runs.get(index)));
        }
        writeExperiments(outputDirectory, experiments);
    }

    public static void writeExperiments(Path outputDirectory, List<ExperimentRun> runs)
            throws IOException {
        var keys = new HashSet<RunKey>();
        for (var run : runs) {
            if (!keys.add(new RunKey(run.scenarioId(), run.repetition()))) {
                throw new IllegalArgumentException("Duplicate scenario/repetition: "
                        + run.scenarioId() + " / " + run.repetition());
            }
        }
        Files.createDirectories(outputDirectory);
        writeRuns(outputDirectory.resolve("runs.csv"), runs);
        writeGenerations(outputDirectory.resolve("generations.csv"), runs);
        writeGenotypes(outputDirectory.resolve("genotype-frequencies.csv"), runs);
    }

    private static void writeRuns(Path file, List<ExperimentRun> runs) throws IOException {
        try (var writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            line(writer, "seed,status,completed_generations,side_length,population_size,maximum_generations,"
                    + "scenario_id,repetition,ticks_per_generation,rounds_per_match,initial_energy_hundredths,"
                    + "resource_energy_hundredths,living_cost_hundredths,resource_spawn_probability,"
                    + "game_payoff_weight_hundredths,crossover_probability,mutation_probability,tournament_size,"
                    + "model_version,random_generator,java_version,extinct,extinction_generation,pairing_mode");
            for (var experiment : runs) {
                var run = experiment.result();
                var config = run.config();
                boolean extinct = run.status().equals("extinct");
                line(writer, row(run.seed(), run.status(), run.generations().size(), config.sideLength(),
                        config.populationSize(), config.maximumGenerations(), experiment.scenarioId(),
                        experiment.repetition(), config.ticksPerGeneration(), config.roundsPerMatch(),
                        config.initialEnergyHundredths(), config.resourceEnergyHundredths(),
                        config.livingCostHundredths(), config.resourceSpawnProbability(),
                        config.gamePayoffWeightHundredths(), config.crossoverProbability(),
                        config.mutationProbability(), config.tournamentSize(), run.modelVersion(),
                        run.randomGenerator(), run.javaVersion(), extinct,
                        extinct ? run.generations().getLast().generation() : null, run.pairingMode().csvValue()));
            }
        }
    }

    private static void writeGenerations(Path file, List<ExperimentRun> runs)
            throws IOException {
        try (var writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            line(writer, "seed,generation,living_agents,average_energy_hundredths,resource_energy_hundredths,"
                    + "game_payoff,scenario_id,repetition,ticks,survival_fraction,maximum_energy_hundredths,"
                    + "matches,played_rounds,first_round_cooperative_actions,first_round_actions,"
                    + "first_round_cooperation_rate,cooperative_actions,played_actions,cooperation_rate,"
                    + "cc_count,cd_count,dc_count,dd_count,cc_fraction,cd_fraction,dc_fraction,dd_fraction,"
                    + "average_resource_energy_hundredths,average_game_payoff,game_energy_hundredths,"
                    + "spatial_clustering,extinct");
            for (var experiment : runs) {
                for (var metric : experiment.result().generations()) {
                    var game = metric.gameStatistics();
                    line(writer, row(experiment.result().seed(), metric.generation(), metric.livingAgents(),
                            metric.averageEnergyHundredths(), metric.resourceEnergyHundredths(),
                            metric.gamePayoff(), experiment.scenarioId(), experiment.repetition(), metric.ticks(),
                            metric.survivalFraction(), metric.maximumEnergyHundredths(), game.matches(),
                            game.rounds(), game.firstRoundCooperativeActions(), game.firstRoundActions(),
                            game.firstRoundCooperationRate(), game.cooperativeActions(), game.actions(),
                            game.cooperationRate(), game.cc(), game.cd(), game.dc(), game.dd(),
                            game.ccFraction(), game.cdFraction(), game.dcFraction(), game.ddFraction(),
                            metric.averageResourceEnergyHundredths(), metric.averageGamePayoff(),
                            metric.gameEnergyHundredths(), metric.spatialClustering(), metric.extinct()));
                }
            }
        }
    }

    private static void writeGenotypes(Path file, List<ExperimentRun> runs)
            throws IOException {
        try (var writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            line(writer, "seed,generation,genotype,count,frequency,scenario_id,repetition");
            for (var experiment : runs) {
                for (var metric : experiment.result().generations()) {
                    for (String genotype : allGenotypes()) {
                        int count = metric.genotypeCounts().getOrDefault(genotype, 0);
                        double frequency = metric.livingAgents() == 0 ? Double.NaN
                                : (double) count / metric.livingAgents();
                        line(writer, row(experiment.result().seed(), metric.generation(), genotype, count,
                                frequency, experiment.scenarioId(), experiment.repetition()));
                    }
                }
            }
        }
    }

    private static List<String> allGenotypes() {
        List<String> genotypes = new ArrayList<>(32);
        for (int bits = 0; bits < 32; bits++) {
            StringBuilder genotype = new StringBuilder(5);
            for (int gene = 4; gene >= 0; gene--) {
                genotype.append((bits & (1 << gene)) == 0 ? 'C' : 'D');
            }
            genotypes.add(genotype.toString());
        }
        return genotypes;
    }

    private static void line(BufferedWriter writer, String value) throws IOException {
        writer.write(value);
        writer.newLine();
    }

    private static String row(Object... values) {
        return Arrays.stream(values).map(CsvResultsWriter::cell).collect(Collectors.joining(","));
    }

    private static String cell(Object value) {
        if (value == null || value instanceof Double number && Double.isNaN(number)) return "";
        String text = value.toString();
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private record RunKey(String scenarioId, int repetition) {}
}
