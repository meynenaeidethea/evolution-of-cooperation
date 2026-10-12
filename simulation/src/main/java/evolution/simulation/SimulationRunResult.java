package evolution.simulation;

import java.util.List;

public record SimulationRunResult(
        SimulationConfig config,
        long seed,
        String status,
        List<GenerationMetrics> generations
) {}
