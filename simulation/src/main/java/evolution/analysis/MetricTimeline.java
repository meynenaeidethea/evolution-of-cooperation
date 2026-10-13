package evolution.analysis;

import evolution.simulation.SimulationSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/** Pure projection of saved snapshots up to the cursor; it never inspects future metrics. */
public record MetricTimeline(long selectedTick, List<Long> generationStarts,
                             List<Series> cooperation, Series survival, Series energy,
                             List<Series> genotypes) {
    public MetricTimeline {
        generationStarts = List.copyOf(generationStarts);
        cooperation = List.copyOf(cooperation);
        genotypes = List.copyOf(genotypes);
    }

    public record Point(long tick, int generation, double value) {}

    public record Series(String name, List<Point> points) {
        public Series { points = List.copyOf(points); }
    }

    public static MetricTimeline fromSnapshots(List<SimulationSnapshot> snapshots, int cursor) {
        if (cursor < 0 || cursor >= snapshots.size()) {
            throw new IndexOutOfBoundsException("No snapshot at cursor " + cursor);
        }
        var visible = snapshots.subList(0, cursor + 1);
        var starts = new ArrayList<Long>();
        Map<String, Integer> peaks = new HashMap<>();
        int previousGeneration = visible.getFirst().generation();
        for (var snapshot : visible) {
            if (snapshot.generation() != previousGeneration) {
                starts.add(snapshot.elapsedTicks());
                previousGeneration = snapshot.generation();
            }
            snapshot.metrics().generationMetrics().genotypeCounts()
                    .forEach((key, count) -> peaks.merge(key, count, Math::max));
        }
        List<Series> genotypes = peaks.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(4)
                .map(entry -> series(entry.getKey(), visible, snapshot -> {
                    var metrics = snapshot.metrics().generationMetrics();
                    return metrics.livingAgents() == 0 ? Double.NaN
                            : 100.0 * metrics.genotypeCounts().getOrDefault(entry.getKey(), 0)
                            / metrics.livingAgents();
                })).toList();
        return new MetricTimeline(visible.getLast().elapsedTicks(), starts, List.of(
                series("Первые раунды", visible, snapshot -> 100 * snapshot.metrics()
                        .generationMetrics().gameStatistics().firstRoundCooperationRate()),
                series("Все раунды", visible, snapshot -> 100 * snapshot.metrics()
                        .generationMetrics().gameStatistics().cooperationRate())),
                series("Выживаемость", visible, snapshot -> 100 * snapshot.metrics()
                        .generationMetrics().survivalFraction()),
                series("Средняя энергия", visible, snapshot -> snapshot.metrics()
                        .averageEnergyHundredths() / 100), genotypes);
    }

    private static Series series(String name, List<SimulationSnapshot> snapshots,
                                 ToDoubleFunction<SimulationSnapshot> value) {
        return new Series(name, snapshots.stream().map(snapshot -> new Point(snapshot.elapsedTicks(),
                snapshot.generation(), value.applyAsDouble(snapshot))).toList());
    }
}
