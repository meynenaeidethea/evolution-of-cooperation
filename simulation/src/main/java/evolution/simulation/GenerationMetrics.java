package evolution.simulation;

import java.util.Map;
import evolution.game.GameStatistics;

public record GenerationMetrics(
        int generation,
        int livingAgents,
        double averageEnergyHundredths,
        long resourceEnergyHundredths,
        long gamePayoff,
        Map<String, Integer> genotypeCounts,
        int ticks,
        double survivalFraction,
        double maximumEnergyHundredths,
        GameStatistics gameStatistics,
        double averageResourceEnergyHundredths,
        double averageGamePayoff,
        long gameEnergyHundredths,
        double spatialClustering,
        boolean extinct
) {
    public GenerationMetrics {
        genotypeCounts = Map.copyOf(genotypeCounts);
    }
}
