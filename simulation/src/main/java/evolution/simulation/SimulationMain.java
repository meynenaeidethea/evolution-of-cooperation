package evolution.simulation;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class SimulationMain {

    private SimulationMain() {
    }

    public static void main(String[] args) throws Exception {
        Path outputDirectory = args.length == 0 ? Path.of("results") : Path.of(args[0]);
        SimulationRunner runner = new SimulationRunner();
        List<SimulationRunResult> runs = new ArrayList<>();
        for (long seed = 1; seed <= 30; seed++) {
            runs.add(runner.run(SimulationConfig.baseline(), seed));
        }
        CsvResultsWriter.write(outputDirectory, runs);
    }
}
