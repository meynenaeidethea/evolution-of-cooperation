package evolution.simulation;

import java.nio.file.Path;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

public final class SimulationMain {
    private SimulationMain() {}
    public static void main(String[] args) throws Exception {
        Path output = Path.of("results");
        String scenario = "baseline";
        int repetitions = 30, generations = 100, threads = 1;
        boolean snapshots = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--output" -> output = Path.of(args[++i]);
                case "--scenario" -> scenario = args[++i];
                case "--repetitions" -> repetitions = Integer.parseInt(args[++i]);
                case "--generations" -> generations = Integer.parseInt(args[++i]);
                case "--threads" -> threads = Integer.parseInt(args[++i]);
                case "--snapshots" -> snapshots = true;
                case "--help" -> {
                    System.out.println("--output DIR --scenario baseline|all|NAME --repetitions 30 --generations 100 --threads 1 --snapshots");
                    return;
                }
                default -> throw new IllegalArgumentException("Unknown option: " + args[i]);
            }
        }
        if (repetitions < 1 || generations < 1 || threads < 1) throw new IllegalArgumentException("Counts must be positive");
        var scenarios = ExperimentScenarios.all(generations);
        if (!scenario.equals("all") && !scenarios.containsKey(scenario)) throw new IllegalArgumentException("Unknown scenario: " + scenario);
        List<Callable<SimulationRunResult>> jobs = new ArrayList<>();
        Path directory = output;
        boolean capture = snapshots;
        for (var entry : scenarios.entrySet()) {
            if (!scenario.equals("all") && !scenario.equals(entry.getKey())) continue;
            for (int repetition = 1; repetition <= repetitions; repetition++) {
                int r = repetition;
                jobs.add(() -> {
                    var result = new SimulationRunner().run(entry.getKey(), r, entry.getValue(), r, (g, grid) -> {
                        if (capture && r == 1 && (g == 1 || g % 25 == 0 || grid.livingAgents().isEmpty())) {
                            try { WorldSnapshotWriter.write(directory.resolve("snapshots/" + entry.getKey() + "-" + g + ".svg"), g, grid); }
                            catch (java.io.IOException e) { throw new UncheckedIOException(e); }
                        }
                    });
                    System.out.println(entry.getKey() + " seed=" + r + " " + result.status());
                    return result;
                });
            }
        }
        List<SimulationRunResult> runs = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(threads)) {
            // Futures are read in submission order, so CSV is byte-identical for every thread count.
            for (var future : executor.invokeAll(jobs)) runs.add(future.get());
        }
        CsvResultsWriter.write(output, runs);
        System.out.println("Saved " + runs.size() + " runs to " + output);
    }
}
