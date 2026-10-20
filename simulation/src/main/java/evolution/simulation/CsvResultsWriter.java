package evolution.simulation;

import evolution.game.Genotype;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Streams UTF-8 CSV; undefined statistics are empty fields, never zero. */
public final class CsvResultsWriter {
    private CsvResultsWriter() {}

    public static void write(Path directory, List<SimulationRunResult> runs) throws IOException {
        Files.createDirectories(directory);
        try (var runCsv = Files.newBufferedWriter(directory.resolve("runs.csv"), StandardCharsets.UTF_8);
             var generations = Files.newBufferedWriter(directory.resolve("generations.csv"), StandardCharsets.UTF_8);
             var genotypes = Files.newBufferedWriter(directory.resolve("genotype-frequencies.csv"), StandardCharsets.UTF_8)) {
            row(runCsv, "scenario", "repetition", "seed", "model_version", "generator", "java_version", "status",
                    "completed_generations", "extinction_generation", "side_length", "population_size", "ticks_per_generation",
                    "rounds_per_match", "initial_energy_hundredths", "resource_energy_hundredths", "living_cost_hundredths",
                    "resource_spawn_probability", "game_payoff_weight_hundredths", "crossover_probability",
                    "mutation_probability", "tournament_size", "maximum_generations", "pairing_mode");
            row(generations, "scenario", "repetition", "seed", "generation", "ticks", "living_agents", "survival_fraction",
                    "average_energy_hundredths", "maximum_energy_hundredths", "resource_energy_hundredths",
                    "resource_energy_per_initial_agent_hundredths", "game_payoff", "game_payoff_per_initial_agent",
                    "game_energy_hundredths", "matches", "first_round_cooperations", "first_round_cooperation_fraction",
                    "cooperations", "cooperation_fraction", "rounds", "cc", "cd", "dc", "dd",
                    "cc_fraction", "cd_fraction", "dc_fraction", "dd_fraction", "clustering", "extinct");
            row(genotypes, "scenario", "repetition", "seed", "generation", "genotype", "count", "frequency");
            for (var run : runs) {
                var c = run.config();
                row(runCsv, run.scenarioId(), run.repetition(), run.seed(), run.modelVersion(), run.generator(),
                        run.javaVersion(), run.status(), run.generations().size(),
                        run.status().equals("extinct") ? run.generations().size() : null,
                        c.sideLength(), c.populationSize(), c.ticksPerGeneration(), c.roundsPerMatch(),
                        c.initialEnergyHundredths(), c.resourceEnergyHundredths(), c.livingCostHundredths(),
                        c.resourceSpawnProbability(), c.gamePayoffWeightHundredths(), c.crossoverProbability(),
                        c.mutationProbability(), c.tournamentSize(), c.maximumGenerations(), c.pairingMode());
                for (var m : run.generations()) {
                    row(generations, run.scenarioId(), run.repetition(), run.seed(), m.generation(), m.ticks(),
                            m.livingAgents(), m.survivalFraction(), m.averageEnergyHundredths(), m.maximumEnergyHundredths(),
                            m.resourceEnergyHundredths(), (double) m.resourceEnergyHundredths() / c.populationSize(),
                            m.gamePayoff(), (double) m.gamePayoff() / c.populationSize(), m.gameEnergyHundredths(),
                            m.matches(), m.firstRoundCooperations(), m.firstRoundCooperationFraction(),
                            m.cooperations(), m.cooperationFraction(), m.rounds(), m.cc(), m.cd(), m.dc(), m.dd(),
                            GenerationMetrics.ratio(m.cc(), m.rounds()), GenerationMetrics.ratio(m.cd(), m.rounds()),
                            GenerationMetrics.ratio(m.dc(), m.rounds()), GenerationMetrics.ratio(m.dd(), m.rounds()),
                            m.clustering(), m.livingAgents() == 0);
                    for (int index = 0; index < 32; index++) {
                        String genotype = Genotype.fromIndex(index).key();
                        int count = m.genotypeCounts().getOrDefault(genotype, 0);
                        row(genotypes, run.scenarioId(), run.repetition(), run.seed(), m.generation(), genotype,
                                count, GenerationMetrics.ratio(count, m.livingAgents()));
                    }
                }
            }
        }
    }

    private static void row(BufferedWriter writer, Object... values) throws IOException {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) writer.write(',');
            Object value = values[i];
            if (value == null || (value instanceof Double d && !Double.isFinite(d))) continue;
            String text = value.toString();
            if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
                writer.write('"'); writer.write(text.replace("\"", "\"\"")); writer.write('"');
            } else writer.write(text);
        }
        writer.write('\n');
    }
}
