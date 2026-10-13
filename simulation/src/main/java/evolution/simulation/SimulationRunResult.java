package evolution.simulation;

import java.util.List;

public record SimulationRunResult(
        SimulationConfig config,
        long seed,
        String status,
        List<GenerationMetrics> generations,
        String modelVersion,
        String randomGenerator,
        String javaVersion,
        PairingMode pairingMode
) {
    public SimulationRunResult {
        generations = List.copyOf(generations);
        java.util.Objects.requireNonNull(pairingMode);
    }

    public SimulationRunResult(SimulationConfig config, long seed, String status,
                               List<GenerationMetrics> generations) {
        this(config, seed, status, generations, SimulationSession.MODEL_VERSION,
                SimulationSession.RANDOM_GENERATOR, Runtime.version().toString(), PairingMode.LOCAL);
    }

    public SimulationRunResult(SimulationConfig config, long seed, String status, List<GenerationMetrics> generations,
                               String modelVersion, String randomGenerator, String javaVersion) {
        this(config, seed, status, generations, modelVersion, randomGenerator, javaVersion, PairingMode.LOCAL);
    }
}
