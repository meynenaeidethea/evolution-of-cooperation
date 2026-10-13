package evolution.analysis;

import java.util.Arrays;
import java.util.Random;

public final class BootstrapStatistics {
    public static final int RESAMPLES = 20_000;
    public static final long SEED = 20261013L;

    private BootstrapStatistics() {}

    public record Estimate(int n, double mean, double low, double high) {}

    /** Percentile interval of the mean, resampling independent runs or paired run differences. */
    public static Estimate mean(double[] values, String key) {
        double[] finite = Arrays.stream(values).filter(Double::isFinite).toArray();
        if (finite.length == 0) return new Estimate(0, Double.NaN, Double.NaN, Double.NaN);
        double mean = Arrays.stream(finite).average().orElseThrow();
        var random = new Random(SEED ^ key.hashCode());
        double[] means = new double[RESAMPLES];
        for (int sample = 0; sample < means.length; sample++) {
            double sum = 0;
            for (int index = 0; index < finite.length; index++) sum += finite[random.nextInt(finite.length)];
            means[sample] = sum / finite.length;
        }
        Arrays.sort(means);
        return new Estimate(finite.length, mean, quantile(means, .025), quantile(means, .975));
    }

    public static Estimate probability(int successes, int n) {
        if (n < 1 || successes < 0 || successes > n) throw new IllegalArgumentException("Invalid probability counts");
        double p = (double) successes / n;
        double z = 1.959963984540054;
        double divisor = 1 + z * z / n;
        double center = (p + z * z / (2 * n)) / divisor;
        double half = z * Math.sqrt(p * (1 - p) / n + z * z / (4 * n * n)) / divisor;
        return new Estimate(n, p, Math.max(0, center - half), Math.min(1, center + half));
    }

    private static double quantile(double[] sorted, double fraction) {
        double position = fraction * (sorted.length - 1);
        int lower = (int) position;
        return sorted[lower] + (sorted[Math.min(lower + 1, sorted.length - 1)] - sorted[lower]) * (position - lower);
    }
}
