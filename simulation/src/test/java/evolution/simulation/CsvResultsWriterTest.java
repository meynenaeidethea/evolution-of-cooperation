package evolution.simulation;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvResultsWriterTest {

    @Test
    void writesTheThreeSpecifiedCsvFiles() throws Exception {
        Path directory = Files.createTempDirectory("simulation-csv-");
        SimulationRunResult run = new SimulationRunner().run(new SimulationConfig(
                3, 1, 1, 1, 100, 0, 0,
                0.0, 0, 0.0, 0.0, 1, 1
        ), 1L);

        CsvResultsWriter.write(directory, List.of(run));

        assertTrue(Files.exists(directory.resolve("runs.csv")));
        assertTrue(Files.exists(directory.resolve("generations.csv")));
        assertTrue(Files.exists(directory.resolve("genotype-frequencies.csv")));
    }
}
