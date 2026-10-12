package evolution.simulation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class CsvResultsWriter {

    private CsvResultsWriter() {
    }

    public static void write(Path outputDirectory, List<SimulationRunResult> runs)
            throws IOException {
        Files.createDirectories(outputDirectory);
        writeRuns(outputDirectory.resolve("runs.csv"), runs);
        writeGenerations(outputDirectory.resolve("generations.csv"), runs);
        writeGenotypes(outputDirectory.resolve("genotype-frequencies.csv"), runs);
    }

    private static void writeRuns(Path file, List<SimulationRunResult> runs) throws IOException {
        List<String> rows = new ArrayList<>();
        rows.add("seed,status,completed_generations,side_length,population_size,maximum_generations");
        for (SimulationRunResult run : runs) {
            rows.add(run.seed() + "," + run.status() + "," + run.generations().size()
                    + "," + run.config().sideLength() + "," + run.config().populationSize()
                    + "," + run.config().maximumGenerations());
        }
        Files.write(file, rows, StandardCharsets.UTF_8);
    }

    private static void writeGenerations(Path file, List<SimulationRunResult> runs)
            throws IOException {
        List<String> rows = new ArrayList<>();
        rows.add("seed,generation,living_agents,average_energy_hundredths,resource_energy_hundredths,game_payoff");
        for (SimulationRunResult run : runs) {
            for (GenerationMetrics metric : run.generations()) {
                rows.add(run.seed() + "," + metric.generation() + "," + metric.livingAgents()
                        + "," + value(metric.averageEnergyHundredths()) + ","
                        + metric.resourceEnergyHundredths() + "," + metric.gamePayoff());
            }
        }
        Files.write(file, rows, StandardCharsets.UTF_8);
    }

    private static void writeGenotypes(Path file, List<SimulationRunResult> runs)
            throws IOException {
        List<String> rows = new ArrayList<>();
        rows.add("seed,generation,genotype,count,frequency");
        for (SimulationRunResult run : runs) {
            for (GenerationMetrics metric : run.generations()) {
                for (String genotype : allGenotypes()) {
                    int count = metric.genotypeCounts().getOrDefault(genotype, 0);
                    double frequency = metric.livingAgents() == 0 ? Double.NaN
                            : (double) count / metric.livingAgents();
                    rows.add(run.seed() + "," + metric.generation() + "," + genotype
                            + "," + count + "," + value(frequency));
                }
            }
        }
        Files.write(file, rows, StandardCharsets.UTF_8);
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

    private static String value(double number) {
        return Double.isNaN(number) ? "" : Double.toString(number);
    }
}
