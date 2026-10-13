package evolution.simulation;

import java.util.List;

public record SimulationRunResult(
        SimulationConfig config,
        long seed,
        String status,
        List<GenerationMetrics> generations,
        String modelVersion,
        String randomGenerator,
        String javaVersion
) {
    public SimulationRunResult {
        generations = List.copyOf(generations);
    }

    public SimulationRunResult(SimulationConfig config, long seed, String status,
                               List<GenerationMetrics> generations) {
        this(config, seed, status, generations, SimulationSession.MODEL_VERSION,
                SimulationSession.RANDOM_GENERATOR, Runtime.version().toString());
    }
}
