package evolution.experiment;

import evolution.simulation.SimulationConfig;
import evolution.simulation.SimulationRunner;
import evolution.simulation.PairingMode;
import evolution.analysis.SeriesAnalysis;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExperimentSeriesRunnerTest {
    @TempDir Path directory;
    private final SimulationConfig config = new SimulationConfig(3, 4, 2, 1, 1000, 400, 0,
            0.3, 20, 0.8, 0.01, 3, 2);
    private final List<ExperimentScenario> scenarios = List.of(new ExperimentScenario("baseline", config),
            new ExperimentScenario("comparison", config));

    @Test
    void localityChangesOnlyPartnerChoiceAndRecordsItInCsv() throws Exception {
        var plan = ExperimentPlan.scenarios(config, ExperimentPlan.Series.LOCALITY);
        assertEquals(2, plan.size());
        assertSame(config, plan.getFirst().config());
        assertSame(config, plan.getLast().config());
        assertEquals(PairingMode.LOCAL, plan.getFirst().pairingMode());
        assertEquals(PairingMode.GLOBAL, plan.getLast().pairingMode());
        var output = directory.resolve("locality");
        var runs = new ExperimentSeriesRunner().run(plan, 2, output, update -> {});
        assertEquals(PairingMode.GLOBAL, runs.getLast().result().pairingMode());
        assertTrue(Files.readString(output.resolve("runs.csv")).contains("pairing_mode"));
        var analysis = SeriesAnalysis.read(output);
        assertEquals("local", analysis.scenarios().getFirst().runs().getFirst().metadata().get("pairing_mode"));
        assertEquals("global", analysis.scenarios().getLast().runs().getFirst().metadata().get("pairing_mode"));
        assertEquals(2, analysis.effects().getFirst().survival().n());
    }

    @Test
    void usesSameSeedSetForEveryScenarioAndReportsProgress() throws Exception {
        var progress = new ArrayList<ExperimentSeriesRunner.Progress>();
        var output = directory.resolve("series");
        var results = new ExperimentSeriesRunner().run(scenarios, 3, output, progress::add);
        assertEquals(6, results.size());
        assertEquals(List.of(1L, 2L, 3L, 1L, 2L, 3L), results.stream().map(run -> run.result().seed()).toList());
        assertEquals(List.of(1, 2, 3, 1, 2, 3), results.stream().map(run -> run.repetition()).toList());
        assertEquals(List.of("baseline", "baseline", "baseline", "comparison", "comparison", "comparison"),
                results.stream().map(run -> run.scenarioId()).toList());
        for (int index = 0; index < results.size(); index++) {
            var update = progress.get(index);
            assertEquals(index + 1, update.completedRuns());
            assertEquals(6, update.totalRuns());
            assertEquals(results.get(index), update.run());
            assertEquals(new SimulationRunner().run(config, results.get(index).repetition()), results.get(index).result());
        }
        assertThrows(UnsupportedOperationException.class, results::clear);
        assertEquals(7, Files.readAllLines(output.resolve("runs.csv")).size());
        assertEquals(4, Files.readAllLines(output.resolve("baseline/runs.csv")).size());
        assertEquals(4, Files.readAllLines(output.resolve("comparison/runs.csv")).size());
    }

    @Test
    void exportsCompletedScenarioBeforeReportingItsLastRun() throws Exception {
        Path output = directory.resolve("checkpoints");
        new ExperimentSeriesRunner().run(scenarios, 2, output, update -> {
            if (update.run().repetition() == 2) {
                assertTrue(Files.exists(output.resolve(update.run().scenarioId()).resolve("runs.csv")));
                assertTrue(Files.exists(output.resolve(update.run().scenarioId()).resolve("generations.csv")));
                assertTrue(Files.exists(output.resolve(update.run().scenarioId()).resolve("genotype-frequencies.csv")));
            }
        });
    }

    @Test
    void completedScenarioRemainsAvailableIfSeriesIsInterrupted() throws Exception {
        Path output = directory.resolve("interrupted");
        assertThrows(IllegalStateException.class, () -> new ExperimentSeriesRunner().run(scenarios, 2, output,
                update -> { if (update.completedRuns() == 2) throw new IllegalStateException("Stopped"); }));
        assertEquals(3, Files.readAllLines(output.resolve("baseline/runs.csv")).size());
        assertFalse(Files.exists(output.resolve("comparison")));
        assertFalse(Files.exists(output.resolve("runs.csv")));
    }

    @Test
    void refusesExistingOutputWithoutChangingItsContents() throws Exception {
        Path output = directory.resolve("existing");
        Files.createDirectory(output);
        Files.writeString(output.resolve("runs.csv"), "existing results");
        assertThrows(FileAlreadyExistsException.class,
                () -> new ExperimentSeriesRunner().run(scenarios, 1, output, update -> fail("Should not run")));
        assertEquals("existing results", Files.readString(output.resolve("runs.csv")));
        assertFalse(Files.exists(output.resolve("baseline")));
    }

    @Test
    void validatesPlanBeforeCreatingOutput() {
        Path output = directory.resolve("invalid");
        var runner = new ExperimentSeriesRunner();
        assertThrows(IllegalArgumentException.class, () -> runner.run(scenarios, 0, output, update -> {}));
        assertThrows(IllegalArgumentException.class, () -> runner.run(List.of(), 1, output, update -> {}));
        assertThrows(IllegalArgumentException.class,
                () -> runner.run(List.of(scenarios.getFirst(), scenarios.getFirst()), 1, output, update -> {}));
        assertFalse(Files.exists(output));
    }

    @Test
    void extinctionDoesNotPreventOtherRepetitionsFromRunning() throws Exception {
        var dying = new SimulationConfig(3, 1, 5, 1, 100, 0, 100, 0, 0, 0, 0, 1, 2);
        var results = new ExperimentSeriesRunner().run(List.of(new ExperimentScenario("extinction", dying)),
                3, directory.resolve("dying"), update -> {});
        assertEquals(3, results.size());
        assertTrue(results.stream().allMatch(run -> run.result().status().equals("extinct")));
    }

    @Test
    void reproducesAllCombinedAndScenarioCsvFiles() throws Exception {
        var runner = new ExperimentSeriesRunner();
        Path first = directory.resolve("first");
        Path second = directory.resolve("second");
        assertEquals(runner.run(scenarios, 2, first, update -> {}),
                runner.run(scenarios, 2, second, update -> {}));
        for (String folder : List.of("", "baseline", "comparison")) {
            for (String file : List.of("runs.csv", "generations.csv", "genotype-frequencies.csv")) {
                assertArrayEquals(Files.readAllBytes(first.resolve(folder).resolve(file)),
                        Files.readAllBytes(second.resolve(folder).resolve(file)));
            }
        }
    }
}
