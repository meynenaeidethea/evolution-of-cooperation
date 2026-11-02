package evolution.analysis;

import evolution.simulation.CsvResultsWriter;
import evolution.simulation.ExperimentRun;
import evolution.simulation.SimulationConfig;
import evolution.simulation.SimulationRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SeriesAnalysisTest {
    @TempDir Path directory;
    private final SimulationConfig config = new SimulationConfig(3, 4, 2, 1, 1000, 400, 100,
            .3, 20, .8, .01, 3, 2);

    private void export() throws Exception {
        var runs = new ArrayList<ExperimentRun>();
        for (String scenario : List.of("baseline", "same")) {
            for (int seed = 1; seed <= 3; seed++) runs.add(new ExperimentRun(scenario, seed, new SimulationRunner().run(config, seed)));
        }
        CsvResultsWriter.writeExperiments(directory, runs);
    }

    @Test
    void identicalScenariosHaveZeroPairedEffectsAndAllGenotypes() throws Exception {
        export();
        var report = SeriesAnalysis.read(directory);
        assertEquals(2, report.horizon());
        assertEquals(2, report.scenarios().size());
        var base = report.scenarios().getFirst();
        assertEquals(3, base.completion().n());
        assertEquals(1, base.completion().mean());
        assertEquals(1, base.survival().mean());
        assertEquals(32, base.genotypeFrequencies().size());
        assertEquals(1, base.genotypeFrequencies().values().stream().mapToDouble(Double::doubleValue).sum(), 1e-10);
        assertEquals(0, report.effects().getFirst().cooperation().mean());
        assertEquals(0, report.effects().getFirst().survival().high());
        assertEquals(2, base.trajectory().size());
        assertEquals(2, base.genotypeTrajectory().size());
        for (var point : base.genotypeTrajectory()) {
            assertEquals(3, point.n());
            assertEquals(32, point.frequencies().size());
            assertEquals(1, point.frequencies().values().stream().mapToDouble(Double::doubleValue).sum(), 1e-10);
            assertThrows(UnsupportedOperationException.class, () -> point.frequencies().clear());
        }
        assertEquals(report, SeriesAnalysis.read(directory));
        assertThrows(UnsupportedOperationException.class, () -> base.runs().clear());
        assertThrows(UnsupportedOperationException.class, () -> base.genotypeFrequencies().clear());
    }

    @Test
    void extinctionIsZeroPopulationButUndefinedCooperationAndEnergy() throws Exception {
        var dying = new SimulationConfig(3, 1, 5, 1, 100, 0, 100, 0, 0, 0, 0, 1, 2);
        CsvResultsWriter.writeExperiments(directory, List.of(new ExperimentRun("dying", 1, new SimulationRunner().run(dying, 7))));
        var scenario = SeriesAnalysis.read(directory).scenarios().getFirst();
        assertEquals(0, scenario.survival().mean());
        assertEquals(0, scenario.cooperation().n());
        assertTrue(Double.isNaN(scenario.cooperation().mean()));
        assertEquals(0, scenario.energy().n());
        assertEquals(0, scenario.trajectory().getLast().survival().mean());
        assertTrue(scenario.genotypeFrequencies().isEmpty());
        assertTrue(scenario.genotypeTrajectory().stream().allMatch(p -> p.n() == 0 && p.frequencies().isEmpty()));
    }

    @Test
    void pairsByActualSeedRatherThanRepetitionOrRowOrder() throws Exception {
        var runner = new SimulationRunner();
        CsvResultsWriter.writeExperiments(directory, List.of(
                new ExperimentRun("baseline", 1, runner.run(config, 7)),
                new ExperimentRun("baseline", 2, runner.run(config, 8)),
                new ExperimentRun("same", 4, runner.run(config, 8)),
                new ExperimentRun("same", 5, runner.run(config, 7))));
        var report = SeriesAnalysis.read(directory);
        assertEquals(2, report.effects().getFirst().cooperation().n());
        assertEquals(0, report.effects().getFirst().cooperation().mean());
    }

    @Test
    void readsQuotedAndMultilineScenarioIds() throws Exception {
        String id = "<sample>, \"quoted\"\nnext";
        CsvResultsWriter.writeExperiments(directory, List.of(new ExperimentRun(id, 1, new SimulationRunner().run(config, 7))));
        assertEquals(id, SeriesAnalysis.read(directory).scenarios().getFirst().id());
    }

    @Test
    void rejectsMissingGenerationAndMissingGenotypeRecords() throws Exception {
        export();
        Path generations = directory.resolve("generations.csv");
        var lines = Files.readAllLines(generations);
        Files.write(generations, lines.subList(0, lines.size() - 1));
        assertThrows(IOException.class, () -> SeriesAnalysis.read(directory));
        Files.write(generations, lines);
        Path genotypes = directory.resolve("genotype-frequencies.csv");
        var frequencies = Files.readAllLines(genotypes);
        Files.write(genotypes, frequencies.subList(0, frequencies.size() - 1));
        assertThrows(IOException.class, () -> SeriesAnalysis.read(directory));
    }

    @Test
    void rejectsDuplicateRunAndGenotypeRows() throws Exception {
        export();
        Path runs = directory.resolve("runs.csv");
        String originalRuns = Files.readString(runs);
        Files.writeString(runs, Files.readAllLines(runs).get(1) + System.lineSeparator(), java.nio.file.StandardOpenOption.APPEND);
        assertThrows(IOException.class, () -> SeriesAnalysis.read(directory));
        Files.writeString(runs, originalRuns);
        Path genotypes = directory.resolve("genotype-frequencies.csv");
        Files.writeString(genotypes, Files.readAllLines(genotypes).get(1) + System.lineSeparator(), java.nio.file.StandardOpenOption.APPEND);
        assertThrows(IOException.class, () -> SeriesAnalysis.read(directory));
    }
}
