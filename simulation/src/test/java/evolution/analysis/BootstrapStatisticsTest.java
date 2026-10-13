package evolution.analysis;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BootstrapStatisticsTest {
    @Test
    void constantSampleHasExactIntervalAndMissingValuesAreExcluded() {
        var estimate = BootstrapStatistics.mean(new double[]{2, 2, Double.NaN, 2}, "fixed");
        assertEquals(new BootstrapStatistics.Estimate(3, 2, 2, 2), estimate);
        assertEquals(0, BootstrapStatistics.mean(new double[]{Double.NaN}, "empty").n());
    }

    @Test
    void bootstrapIsReproducibleAndUsesMeanOfRuns() {
        var values = new double[]{1, 2, 3};
        var estimate = BootstrapStatistics.mean(values, "sample");
        assertEquals(2, estimate.mean());
        assertTrue(estimate.low() < 2 && estimate.high() > 2);
        assertEquals(estimate, BootstrapStatistics.mean(values, "sample"));
    }

    @Test
    void WilsonIntervalsDoNotCollapseForAllOrNoSuccesses() {
        var all = BootstrapStatistics.probability(30, 30);
        assertEquals(1, all.mean());
        assertTrue(all.low() > .88 && all.low() < .89);
        var none = BootstrapStatistics.probability(0, 30);
        assertEquals(0, none.mean());
        assertTrue(none.high() > .11 && none.high() < .12);
        assertThrows(IllegalArgumentException.class, () -> BootstrapStatistics.probability(2, 1));
    }
}
