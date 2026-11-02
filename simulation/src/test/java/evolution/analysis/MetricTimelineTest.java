package evolution.analysis;

import evolution.simulation.SimulationConfig;
import evolution.simulation.SimulationHistory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class MetricTimelineTest {
    private final SimulationConfig config = new SimulationConfig(3, 4, 2, 1, 1000, 400, 100,
            0.3, 20, 0.8, 0.01, 3, 2);

    private SimulationHistory completedHistory() {
        var history = new SimulationHistory(config, 7);
        while (history.canStepForward()) history.stepForward();
        return history;
    }

    @Test
    void projectsPercentagesAndEnergyWithoutLookingPastCursor() {
        var history = completedHistory();
        var data = MetricTimeline.fromSnapshots(history.snapshots(), 2);
        assertEquals(2, data.selectedTick());
        assertEquals(3, data.energy().points().size());
        assertEquals(10, data.energy().points().getFirst().value());
        assertEquals(12.9, data.energy().points().getLast().value());
        assertEquals(100, data.survival().points().getLast().value());
        assertEquals(50, data.cooperation().get(1).points().getLast().value());
        assertTrue(data.generationStarts().isEmpty());
        assertTrue(data.genotypes().stream().noneMatch(series -> series.name().equals("DCCDC")));
        assertTrue(Double.isNaN(data.cooperation().getFirst().points().getFirst().value()));
        assertEquals(25, data.genotypes().getFirst().points().getLast().value());
    }

    @Test
    void retainsGenerationBoundariesAndUsesStableLeaderRanking() {
        var history = completedHistory();
        var data = MetricTimeline.fromSnapshots(history.snapshots(), 4);
        assertEquals(java.util.List.of(3L), data.generationStarts());
        assertEquals("DCCCC", data.genotypes().getFirst().name());
        assertEquals(50, data.genotypes().getFirst().points().getLast().value());
        assertEquals(13.4, data.energy().points().getLast().value());
        assertEquals(1, data.energy().points().get(2).generation());
        assertEquals(2, data.energy().points().get(3).generation());
        assertEquals(0, data.cooperation().get(1).points().getLast().value());
        assertEquals(data, MetricTimeline.fromSnapshots(history.snapshots(), 4));
    }

    @Test
    void rewindAndReplayRestoreExactlyTheSameTimelineAndNewRunClearsIt() {
        var history = completedHistory();
        var saved = history.snapshots();
        var end = MetricTimeline.fromSnapshots(saved, history.cursor());
        history.seek(1);
        assertEquals(2, MetricTimeline.fromSnapshots(saved, history.cursor()).energy().points().size());
        history.toEnd();
        assertEquals(end, MetricTimeline.fromSnapshots(history.snapshots(), history.cursor()));
        assertEquals(saved, history.snapshots());
        history.newRun(config, 8);
        var reset = MetricTimeline.fromSnapshots(history.snapshots(), history.cursor());
        assertEquals(0, reset.selectedTick());
        assertEquals(1, reset.energy().points().size());
        assertTrue(reset.generationStarts().isEmpty());
    }

    @Test
    void extinctionKeepsMissingValuesAsGapsAndSurvivalAsZero() {
        var dying = new SimulationConfig(3, 1, 5, 1, 100, 0, 100,
                0, 0, 0, 0, 1, 2);
        var history = new SimulationHistory(dying, 1);
        history.stepForward();
        var data = MetricTimeline.fromSnapshots(history.snapshots(), 1);
        assertEquals(0, data.survival().points().getLast().value());
        assertTrue(Double.isNaN(data.energy().points().getLast().value()));
        assertTrue(Double.isNaN(data.cooperation().get(1).points().getLast().value()));
        assertEquals(1, data.genotypes().size());
        assertTrue(Double.isNaN(data.genotypes().getFirst().points().getLast().value()));
    }

    @Test
    void outputCollectionsAreDetachedAndCursorIsValidated() {
        var history = completedHistory();
        var source = new ArrayList<>(history.snapshots());
        var data = MetricTimeline.fromSnapshots(source, 4);
        source.clear();
        assertEquals(5, data.energy().points().size());
        assertThrows(UnsupportedOperationException.class, () -> data.energy().points().clear());
        assertThrows(UnsupportedOperationException.class, () -> data.genotypes().clear());
        assertThrows(UnsupportedOperationException.class, () -> data.generationStarts().clear());
        assertThrows(IndexOutOfBoundsException.class, () -> MetricTimeline.fromSnapshots(history.snapshots(), -1));
        assertThrows(IndexOutOfBoundsException.class, () -> MetricTimeline.fromSnapshots(history.snapshots(), 5));
    }
}
