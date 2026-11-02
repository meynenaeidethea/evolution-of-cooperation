package evolution.simulation;

import java.util.ArrayList;
import java.util.List;

/** Single-threaded timeline: navigation never mutates or rewinds the forward-only session. */
public final class SimulationHistory {
    private final List<SimulationSnapshot> snapshots = new ArrayList<>();
    private SimulationSession session;
    private int cursor;
    private final int capacity;

    public SimulationHistory(SimulationConfig config,long seed,int capacity) {
        if (capacity < 2) throw new IllegalArgumentException("History capacity must be >= 2");
        this.capacity=capacity;
        newRun(config,seed);
    }
    public int capacity() { return capacity; }
    public int nearestIndex(long elapsedTick) {
        int best=0;
        for(int i=1;i<snapshots.size();i++) if(Math.abs(snapshots.get(i).elapsedTicks()-elapsedTick)<Math.abs(snapshots.get(best).elapsedTicks()-elapsedTick)) best=i;
        return best;
    }

    public SimulationHistory(SimulationConfig config, long seed) {
        this(config, seed, 300);
    }

    public void newRun(SimulationConfig config, long seed) {
        newRun(config, seed, PairingMode.LOCAL);
    }
    public void newRun(SimulationConfig config,long seed,PairingMode mode) {
        SimulationSession next = new SimulationSession(config, seed, mode);
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
            if (snapshots.size() > capacity) { snapshots.removeFirst(); cursor--; }
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
