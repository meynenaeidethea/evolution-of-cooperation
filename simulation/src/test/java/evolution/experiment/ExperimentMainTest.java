package evolution.experiment;

import evolution.simulation.SimulationConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ExperimentMainTest {
    @TempDir Path directory;
    private final SimulationConfig config = new SimulationConfig(3, 2, 1, 5, 1000, 400, 0,
            0.1, 20, 0.8, 0.01, 3, 1);

    @Test
    void defaultsToThirtyBaselineRepetitions() {
        var options = ExperimentOptions.parse(new String[0]);
        assertEquals(ExperimentPlan.Series.BASELINE, options.series());
        assertEquals(30, options.repetitions());
        assertNull(options.outputDirectory());
        assertFalse(options.listOnly());
    }

    @Test
    void parsesExplicitSeriesAndRejectsInvalidArguments() {
        var options = ExperimentOptions.parse(new String[]{"--series", "rounds", "--repetitions", "2",
                "--output", directory.resolve("series").toString(), "--list"});
        assertEquals(ExperimentPlan.Series.ROUNDS, options.series());
        assertEquals(2, options.repetitions());
        assertTrue(options.listOnly());
        assertEquals(directory.resolve("series"), options.outputDirectory());
        for (String[] args : new String[][]{{"--unknown"}, {"--series"}, {"--series", "unknown"},
                {"--repetitions", "0"}, {"--repetitions", "-1"}, {"--repetitions", "abc"},
                {"--repetitions", "2147483648"}, {"--output", ""}, {"--series", "--list"},
                {"--list", "--list"}, {"--series", "rounds", "--series", "mutation"}}) {
            assertThrows(IllegalArgumentException.class, () -> ExperimentOptions.parse(args));
        }
    }

    @Test
    void previewAndHelpDoNotCreateOutputOrRunSimulations() throws Exception {
        Path target = directory.resolve("preview");
        String preview = execute("--series", "all", "--output", target.toString(), "--list");
        assertTrue(preview.contains("Сценариев: 18"));
        assertTrue(preview.contains("Всего запусков: 540"));
        assertTrue(preview.contains("seed = номер повтора: 1…30"));
        assertTrue(preview.contains("ticksPerGeneration=1"));
        assertFalse(preview.contains("[1/"));
        assertFalse(Files.exists(target));
        assertTrue(execute("--help", "--output", target.toString()).contains("--repetitions"));
        assertFalse(Files.exists(target));
    }

    @Test
    void smallSeriesProducesCompleteCsvAndProgressThroughEntryPoint() throws Exception {
        Path target = directory.resolve("small");
        String log = execute("--series", "rounds", "--repetitions", "2", "--output", target.toString());
        assertTrue(log.contains("[8/8]"));
        assertTrue(log.contains("Серия завершена"));
        assertTrue(log.contains("проверочная серия"));
        assertEquals(9, Files.readAllLines(target.resolve("runs.csv")).size());
        assertEquals(9, Files.readAllLines(target.resolve("generations.csv")).size());
        assertEquals(257, Files.readAllLines(target.resolve("genotype-frequencies.csv")).size());
        assertTrue(Files.exists(target.resolve("rounds-1/runs.csv")));
    }

    private String execute(String... args) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            ExperimentMain.execute(args, config, output);
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }
}
