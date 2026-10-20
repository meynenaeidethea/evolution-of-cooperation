package evolution.simulation;

import java.util.List;

public record SimulationRunResult(
        String scenarioId, int repetition, SimulationConfig config, long seed,
        String modelVersion, String generator, String javaVersion,
        String status, List<GenerationMetrics> generations
) {
    public static final String MODEL_VERSION = "1.0.0";
    public static final String GENERATOR = "L64X128MixRandom";
    public SimulationRunResult { generations = List.copyOf(generations); }
}
