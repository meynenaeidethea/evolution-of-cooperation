package evolution.experiment;

import evolution.simulation.CsvResultsWriter;
import evolution.simulation.ExperimentRun;
import evolution.simulation.SimulationRunner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Sequential runs with common seed = repetition and a CSV checkpoint per completed scenario. */
public final class ExperimentSeriesRunner {
    public static final int DEFAULT_REPETITIONS = 30;

    public record Progress(long completedRuns, long totalRuns, ExperimentRun run) {}

    public List<ExperimentRun> run(List<ExperimentScenario> scenarios, int repetitions,
                                   Path outputDirectory, Consumer<Progress> progress) throws IOException {
        scenarios = List.copyOf(scenarios);
        Objects.requireNonNull(outputDirectory);
        Objects.requireNonNull(progress);
        if (scenarios.isEmpty()) throw new IllegalArgumentException("At least one scenario is required");
        if (repetitions < 1) throw new IllegalArgumentException("Repetitions must be positive");
        var ids = new HashSet<String>();
        for (var scenario : scenarios) {
            if (!ids.add(scenario.id())) throw new IllegalArgumentException("Duplicate scenario: " + scenario.id());
        }
        Path output = outputDirectory.toAbsolutePath().normalize();
        Files.createDirectories(output.getParent());
        // Refuse even an empty existing directory: previous results are never overwritten by a series.
        Files.createDirectory(output);
        var runner = new SimulationRunner();
        var results = new ArrayList<ExperimentRun>();
        long total = (long) scenarios.size() * repetitions;
        long completed = 0;
        for (var scenario : scenarios) {
            var scenarioResults = new ArrayList<ExperimentRun>();
            for (int index = 0; index < repetitions; index++) {
                int repetition = index + 1;
                var result = runner.run(scenario.config(), repetition, scenario.pairingMode());
                var experiment = new ExperimentRun(scenario.id(), repetition, result);
                scenarioResults.add(experiment);
                results.add(experiment);
                completed++;
                if (repetition == repetitions) {
                    CsvResultsWriter.writeExperiments(output.resolve(scenario.id()), scenarioResults);
                }
                progress.accept(new Progress(completed, total, experiment));
            }
        }
        CsvResultsWriter.writeExperiments(output, results);
        return List.copyOf(results);
    }
}
