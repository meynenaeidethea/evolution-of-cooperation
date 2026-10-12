package evolution.simulation;

import java.util.Map;

public record GenerationMetrics(
        int generation,
        int livingAgents,
        double averageEnergyHundredths,
        int resourceEnergyHundredths,
        int gamePayoff,
        Map<String, Integer> genotypeCounts
) {}
