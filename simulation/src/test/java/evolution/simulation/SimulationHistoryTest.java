package evolution.simulation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SimulationHistoryTest {
    private final SimulationConfig config = new SimulationConfig(3, 4, 2, 1, 1000, 400, 0,
            0.3, 20, 0.8, 0.01, 3, 2);

    @Test
    void savesInitialStateAndExactlyOneSnapshotPerTick() {
        var history = new SimulationHistory(config, 7);
        var initial = history.currentSnapshot();
        assertEquals(1, history.size());
        assertSame(initial, history.back());
        assertSame(initial, history.toEnd());
        assertEquals(0, history.cursor());
        assertTrue(history.canStepForward());
        var first = history.stepForward();
        assertEquals(2, history.size());
        assertEquals(1, history.cursor());
        assertEquals(1, first.elapsedTicks());
        assertSame(initial, history.back());
        assertSame(first, history.stepForward());
        assertEquals(2, history.size());
    }

    @Test
    void replayAcrossGenerationBoundaryUsesSameObjectsAndDoesNotConsumeRandomness() {
        var history = new SimulationHistory(config, 7);
        var control = new SimulationHistory(config, 7);
        for (int i = 0; i < 3; i++) {
            history.stepForward();
            control.stepForward();
        }
        var saved = history.snapshots();
        history.toStart();
        assertSame(saved.get(1), history.stepForward());
        assertSame(saved.get(2), history.stepForward());
        assertSame(saved.get(3), history.stepForward());
        assertEquals(4, history.size());
        assertEquals(control.stepForward(), history.stepForward());
        assertFalse(history.canStepForward());
        assertSame(history.currentSnapshot(), history.stepForward());
        assertEquals(5, history.size());
        history.back();
        assertTrue(history.canStepForward());
        assertSame(history.snapshots().getLast(), history.toEnd());
    }

    @Test
    void seekingChangesOnlyCursorAndValidatesBounds() {
        var history = new SimulationHistory(config, 7);
        history.stepForward();
        history.stepForward();
        var saved = history.snapshots();
        assertSame(saved.get(1), history.seek(1));
        assertSame(saved.getFirst(), history.toStart());
        assertSame(saved.getLast(), history.toEnd());
        assertEquals(saved, history.snapshots());
        assertThrows(IndexOutOfBoundsException.class, () -> history.seek(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> history.seek(3));
        assertEquals(2, history.cursor());
        assertThrows(UnsupportedOperationException.class, () -> saved.clear());
    }

    @Test
    void newRunClearsHistoryAndRecreatesSeedEvenFromPastOrFinishedState() {
        var history = new SimulationHistory(config, 7);
        var initial = history.currentSnapshot();
        while (history.canStepForward()) {
            history.stepForward();
        }
        var old = history.snapshots();
        history.seek(1);
        history.newRun(config, 8);
        assertEquals(1, history.size());
        assertEquals(0, history.cursor());
        assertEquals(8, history.currentSnapshot().seed());
        assertEquals(new SimulationHistory(config, 8).currentSnapshot(), history.currentSnapshot());
        history.newRun(config, 7);
        assertEquals(initial, history.currentSnapshot());
        assertTrue(history.canStepForward());
        assertEquals(5, old.size());
        assertEquals("completed", old.getLast().status());
    }

    @Test
    void extinctionStopsExtensionButAllowsReplay() {
        var dying = new SimulationConfig(3, 4, 2, 1, 100, 0, 100,
                0, 0, 0.8, 0.01, 3, 2);
        var history = new SimulationHistory(dying, 7);
        var last = history.stepForward();
        assertEquals("extinct", last.status());
        assertFalse(history.canStepForward());
        assertSame(last, history.stepForward());
        history.toStart();
        assertTrue(history.canStepForward());
        assertSame(last, history.stepForward());
        assertEquals(2, history.size());
    }
}
