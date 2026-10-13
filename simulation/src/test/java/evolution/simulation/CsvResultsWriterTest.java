package evolution.simulation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CsvResultsWriterTest {
    @TempDir Path directory;

    private final SimulationConfig config = new SimulationConfig(3, 4, 2, 1, 1000, 400, 100,
            0.3, 20, 0.8, 0.01, 3, 2);

    private SimulationRunResult run() {
        return new SimulationRunner().run(config, 7);
    }

    @Test
    void writesTheThreeSpecifiedCsvFiles() throws Exception {
        SimulationRunResult run = new SimulationRunner().run(new SimulationConfig(
                3, 1, 1, 1, 100, 0, 0,
                0.0, 0, 0.0, 0.0, 1, 1
        ), 1L);

        CsvResultsWriter.write(directory, List.of(run));

        assertTrue(Files.exists(directory.resolve("runs.csv")));
        assertTrue(Files.exists(directory.resolve("generations.csv")));
        assertTrue(Files.exists(directory.resolve("genotype-frequencies.csv")));
        assertEquals(
                33,
                Files.readAllLines(directory.resolve("genotype-frequencies.csv")).size()
        );
    }

    @Test
    void exportsEveryParameterAndRunProvenance() throws Exception {
        CsvResultsWriter.write(directory, List.of(run(), run()));
        var rows = read("runs.csv");
        var first = rows.getFirst();
        assertEquals("7", first.get("seed"));
        assertEquals("completed", first.get("status"));
        assertEquals("2", first.get("completed_generations"));
        assertEquals("baseline", first.get("scenario_id"));
        assertEquals("1", first.get("repetition"));
        assertEquals("2", rows.get(1).get("repetition"));
        assertEquals("3", first.get("side_length"));
        assertEquals("4", first.get("population_size"));
        assertEquals("2", first.get("ticks_per_generation"));
        assertEquals("1", first.get("rounds_per_match"));
        assertEquals("1000", first.get("initial_energy_hundredths"));
        assertEquals("400", first.get("resource_energy_hundredths"));
        assertEquals("100", first.get("living_cost_hundredths"));
        assertEquals("0.3", first.get("resource_spawn_probability"));
        assertEquals("20", first.get("game_payoff_weight_hundredths"));
        assertEquals("0.8", first.get("crossover_probability"));
        assertEquals("0.01", first.get("mutation_probability"));
        assertEquals("3", first.get("tournament_size"));
        assertEquals("2", first.get("maximum_generations"));
        assertEquals("genetic-v1", first.get("model_version"));
        assertEquals("L64X128MixRandom", first.get("random_generator"));
        assertEquals(Runtime.version().toString(), first.get("java_version"));
        assertEquals("false", first.get("extinct"));
        assertEquals("", first.get("extinction_generation"));
    }

    @Test
    void exportsExtendedMetricsWithCorrectUnitsAndDenominators() throws Exception {
        CsvResultsWriter.write(directory, List.of(run()));
        var rows = read("generations.csv");
        var first = rows.getFirst();
        assertEquals("2", first.get("ticks"));
        assertEquals("1.0", first.get("survival_fraction"));
        assertEquals("1290.0", first.get("average_energy_hundredths"));
        assertTrue(Double.parseDouble(first.get("maximum_energy_hundredths")) >= 1290);
        assertEquals("1600", first.get("resource_energy_hundredths"));
        assertEquals("400.0", first.get("average_resource_energy_hundredths"));
        assertEquals("18", first.get("game_payoff"));
        assertEquals("4.5", first.get("average_game_payoff"));
        assertEquals("360", first.get("game_energy_hundredths"));
        assertEquals("4", first.get("matches"));
        assertEquals("4", first.get("played_rounds"));
        assertEquals("4", first.get("first_round_cooperative_actions"));
        assertEquals("8", first.get("first_round_actions"));
        assertEquals("0.5", first.get("first_round_cooperation_rate"));
        assertEquals("4", first.get("cooperative_actions"));
        assertEquals("8", first.get("played_actions"));
        assertEquals("0.5", first.get("cooperation_rate"));
        assertEquals("1", first.get("cc_count"));
        assertEquals("1", first.get("dd_count"));
        assertEquals("0.25", first.get("cc_fraction"));
        assertEquals("0.25", first.get("dd_fraction"));
        assertEquals("0.0", first.get("spatial_clustering"));
        double fractions = List.of("cc_fraction", "cd_fraction", "dc_fraction", "dd_fraction")
                .stream().mapToDouble(name -> Double.parseDouble(first.get(name))).sum();
        assertEquals(1, fractions);
        assertEquals("0.0", rows.get(1).get("cooperation_rate"));
        assertEquals("4", rows.get(1).get("dd_count"));
        var genotypes = read("genotype-frequencies.csv");
        assertEquals(64, genotypes.size());
        assertEquals(32, genotypes.subList(0, 32).stream().map(row -> row.get("genotype")).distinct().count());
        assertEquals(1, genotypes.subList(0, 32).stream().mapToDouble(row -> Double.parseDouble(row.get("frequency"))).sum());
    }

    @Test
    void exportsEmptyFieldsForUndefinedMetricsAfterExtinction() throws Exception {
        var dying = new SimulationConfig(3, 1, 5, 1, 100, 0, 100,
                0, 0, 0, 0, 1, 2);
        CsvResultsWriter.write(directory, List.of(new SimulationRunner().run(dying, 1)));
        var first = read("generations.csv").getFirst();
        for (String name : List.of("average_energy_hundredths", "maximum_energy_hundredths",
                "first_round_cooperation_rate", "cooperation_rate", "cc_fraction", "cd_fraction",
                "dc_fraction", "dd_fraction", "spatial_clustering")) {
            assertEquals("", first.get(name), name);
        }
        assertEquals("0.0", first.get("survival_fraction"));
        assertEquals("true", first.get("extinct"));
        var metadata = read("runs.csv").getFirst();
        assertEquals("extinct", metadata.get("status"));
        assertEquals("1", metadata.get("extinction_generation"));
        var genotypes = read("genotype-frequencies.csv");
        assertEquals(32, genotypes.size());
        for (var genotype : genotypes) {
            assertEquals("0", genotype.get("count"));
            assertEquals("", genotype.get("frequency"));
        }
    }

    @Test
    void separatesScenariosWithSameSeedAndPreservesExplicitRepetitions() throws Exception {
        CsvResultsWriter.writeExperiments(directory, List.of(new ExperimentRun("K=1", 3, run()),
                new ExperimentRun("K=5", 3, run())));
        for (String file : List.of("runs.csv", "generations.csv", "genotype-frequencies.csv")) {
            var rows = read(file);
            assertEquals(List.of("K=1", "K=5"), rows.stream().map(row -> row.get("scenario_id")).distinct().toList());
            assertTrue(rows.stream().allMatch(row -> row.get("repetition").equals("3")));
            assertTrue(rows.stream().allMatch(row -> row.get("seed").equals("7")));
        }
    }

    @Test
    void escapesScenarioIdsAndRejectsDuplicateIdentitiesBeforeWriting() throws Exception {
        String scenario = "сценарий, \"A\"\nстрока";
        CsvResultsWriter.writeExperiments(directory, List.of(new ExperimentRun(scenario, 1, run())));
        for (String file : List.of("runs.csv", "generations.csv", "genotype-frequencies.csv")) {
            assertTrue(Files.readString(directory.resolve(file)).contains("\"сценарий, \"\"A\"\"\nстрока\""));
        }
        Path duplicateDirectory = directory.resolve("duplicates");
        var experiment = new ExperimentRun("baseline", 1, run());
        assertThrows(IllegalArgumentException.class,
                () -> CsvResultsWriter.writeExperiments(duplicateDirectory, List.of(experiment, experiment)));
        assertFalse(Files.exists(duplicateDirectory));
        assertThrows(IllegalArgumentException.class, () -> new ExperimentRun(" ", 1, run()));
        assertThrows(IllegalArgumentException.class, () -> new ExperimentRun("base", 0, run()));
    }

    @Test
    void repeatsCsvBytesExactlyForSameScenarioParametersAndSeed() throws Exception {
        Path first = directory.resolve("first");
        Path second = directory.resolve("second");
        CsvResultsWriter.write(first, List.of(run()));
        CsvResultsWriter.write(second, List.of(run()));
        for (String file : List.of("runs.csv", "generations.csv", "genotype-frequencies.csv")) {
            assertArrayEquals(Files.readAllBytes(first.resolve(file)), Files.readAllBytes(second.resolve(file)));
        }
    }

    @Test
    void emptySeriesStillWritesHeaders() throws Exception {
        CsvResultsWriter.write(directory, List.of());
        for (String file : List.of("runs.csv", "generations.csv", "genotype-frequencies.csv")) {
            assertEquals(1, Files.readAllLines(directory.resolve(file)).size());
        }
    }

    private List<Map<String, String>> read(String file) throws Exception {
        var lines = Files.readAllLines(directory.resolve(file));
        String[] headers = lines.getFirst().split(",", -1);
        return lines.stream().skip(1).map(line -> {
            String[] cells = line.split(",", -1);
            assertEquals(headers.length, cells.length, "CSV column count");
            Map<String, String> row = new LinkedHashMap<>();
            for (int i = 0; i < headers.length; i++) row.put(headers[i], cells[i]);
            return row;
        }).toList();
    }
}
