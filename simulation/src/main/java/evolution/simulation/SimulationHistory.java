package evolution.simulation;

import java.util.ArrayList;
import java.util.List;

/** Single-threaded timeline: navigation never mutates or rewinds the forward-only session. */
public final class SimulationHistory {
    private final List<SimulationSnapshot> snapshots = new ArrayList<>();
    private SimulationSession session;
    private int cursor;

    public SimulationHistory(SimulationConfig config, long seed) {
        newRun(config, seed);
    }

    public void newRun(SimulationConfig config, long seed) {
        SimulationSession next = new SimulationSession(config, seed);
        SimulationSnapshot initial = next.currentSnapshot();
        session = next;
        snapshots.clear();
        snapshots.add(initial);
        cursor = 0;
    }

    public SimulationSnapshot currentSnapshot() {
        return snapshots.get(cursor);
    }

    public int cursor() {
        return cursor;
    }

    public int size() {
        return snapshots.size();
    }

    public List<SimulationSnapshot> snapshots() {
        return List.copyOf(snapshots);
    }

    public boolean canStepForward() {
        return cursor < snapshots.size() - 1 || !session.isFinished();
    }

    /** Replays a saved tick if possible; only the tip of history can extend the run. */
    public SimulationSnapshot stepForward() {
        if (cursor < snapshots.size() - 1) {
            cursor++;
        } else if (!session.isFinished()) {
            session.advance();
            snapshots.add(session.currentSnapshot());
            cursor++;
        }
        return currentSnapshot();
    }

    public SimulationSnapshot back() {
        return seek(Math.max(0, cursor - 1));
    }

    public SimulationSnapshot toStart() {
        return seek(0);
    }

    /** Goes to the last saved tick, without calculating future ticks. */
    public SimulationSnapshot toEnd() {
        return seek(snapshots.size() - 1);
    }

    public SimulationSnapshot seek(int index) {
        if (index < 0 || index >= snapshots.size()) {
            throw new IndexOutOfBoundsException("No saved snapshot at index " + index);
        }
        cursor = index;
        return currentSnapshot();
    }
}
