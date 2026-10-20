package evolution.simulation;

import java.util.Map;

public record GenerationMetrics(
        int generation, int ticks, int livingAgents, double survivalFraction,
        double averageEnergyHundredths, double maximumEnergyHundredths,
        long resourceEnergyHundredths, long gamePayoff, long gameEnergyHundredths,
        long matches, long firstRoundCooperations, long cooperations,
        long cc, long cd, long dc, long dd, double clustering,
        Map<String, Integer> genotypeCounts
) {
    public GenerationMetrics { genotypeCounts = Map.copyOf(genotypeCounts); }
    public long rounds() { return cc + cd + dc + dd; }
    public double firstRoundCooperationFraction() { return ratio(firstRoundCooperations, 2 * matches); }
    public double cooperationFraction() { return ratio(cooperations, 2 * rounds()); }
    public static double ratio(long numerator, long denominator) {
        return denominator == 0 ? Double.NaN : (double) numerator / denominator;
    }
}
