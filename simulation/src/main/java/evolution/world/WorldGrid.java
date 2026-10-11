package evolution.world;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class WorldGrid {

    private final int sideLength;
    private final Map<CellPosition, Agent> agentsByPosition = new HashMap<>();
    private final boolean[][] resources;

    public WorldGrid(int sideLength) {
        if (sideLength < 3) {
            throw new IllegalArgumentException("Side length must be at least 3");
        }

        this.sideLength = sideLength;
        this.resources = new boolean[sideLength][sideLength];
    }

    public int sideLength() {
        return sideLength;
    }

    public void placeAgent(Agent agent) {
        Objects.requireNonNull(agent, "Agent must be defined");
        requireInsideGrid(agent.position());

        if (agentsByPosition.containsKey(agent.position())) {
            throw new IllegalArgumentException("Cell is already occupied by an agent");
        }

        agentsByPosition.put(agent.position(), agent);
    }

    public Optional<Agent> agentAt(CellPosition position) {
        requireInsideGrid(position);
        return Optional.ofNullable(agentsByPosition.get(position));
    }

    public void placeResource(CellPosition position) {
        requireInsideGrid(position);
        resources[position.row()][position.column()] = true;
    }

    public boolean hasResourceAt(CellPosition position) {
        requireInsideGrid(position);
        return resources[position.row()][position.column()];
    }

    public boolean takeResourceAt(CellPosition position) {
        requireInsideGrid(position);

        if (!resources[position.row()][position.column()]) {
            return false;
        }

        resources[position.row()][position.column()] = false;
        return true;
    }

    private void requireInsideGrid(CellPosition position) {
        Objects.requireNonNull(position, "Position must be defined");

        if (position.row() < 0 || position.row() >= sideLength
                || position.column() < 0 || position.column() >= sideLength) {
            throw new IllegalArgumentException("Position must be inside the grid");
        }
    }
}
