package evolution.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;
import java.util.random.RandomGenerator;

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

    public List<Agent> livingAgents() {
        return agentsByPosition.values().stream()
                .filter(Agent::isAlive)
                .sorted(Comparator.comparingLong(Agent::agentId))
                .toList();
    }

    public List<Agent> livingNeighbours(Agent agent) {
        Objects.requireNonNull(agent, "Agent must be defined");

        if (agentsByPosition.get(agent.position()) != agent) {
            throw new IllegalArgumentException("Agent must be placed on this grid");
        }

        List<Agent> neighbours = new ArrayList<>();
        for (int rowDelta = -1; rowDelta <= 1; rowDelta++) {
            for (int columnDelta = -1; columnDelta <= 1; columnDelta++) {
                if (rowDelta == 0 && columnDelta == 0) {
                    continue;
                }

                CellPosition position = agent.position().moveBy(
                        rowDelta, columnDelta, sideLength
                );
                Agent neighbour = agentsByPosition.get(position);
                if (neighbour != null && neighbour.isAlive()) {
                    neighbours.add(neighbour);
                }
            }
        }
        return List.copyOf(neighbours);
    }

    public List<CellPosition> availableMoveTargets(Agent agent) {
        Objects.requireNonNull(agent, "Agent must be defined");

        if (agentsByPosition.get(agent.position()) != agent) {
            throw new IllegalArgumentException("Agent must be placed on this grid");
        }

        List<CellPosition> targets = new ArrayList<>();
        targets.add(agent.position());

        for (int rowDelta = -1; rowDelta <= 1; rowDelta++) {
            for (int columnDelta = -1; columnDelta <= 1; columnDelta++) {
                if (rowDelta == 0 && columnDelta == 0) {
                    continue;
                }

                CellPosition target = agent.position().moveBy(
                        rowDelta,
                        columnDelta,
                        sideLength
                );

                if (!agentsByPosition.containsKey(target)) {
                    targets.add(target);
                }
            }
        }

        return List.copyOf(targets);
    }

    public void moveAgents(Map<Agent, CellPosition> moves) {
        Objects.requireNonNull(moves, "Moves must be defined");
        Set<CellPosition> destinations = new HashSet<>();

        for (Map.Entry<Agent, CellPosition> move : moves.entrySet()) {
            Agent agent = Objects.requireNonNull(move.getKey(), "Agent must be defined");
            CellPosition target = Objects.requireNonNull(move.getValue(), "Target must be defined");

            if (!agent.isAlive()) {
                throw new IllegalArgumentException("Dead agent cannot move");
            }
            if (agentsByPosition.get(agent.position()) != agent) {
                throw new IllegalArgumentException("Agent must be placed on this grid");
            }

            requireInsideGrid(target);

            if (!destinations.add(target)) {
                throw new IllegalArgumentException("Multiple agents cannot move to the same cell");
            }

            Agent initialOccupant = agentsByPosition.get(target);
            if (initialOccupant != null && initialOccupant != agent) {
                throw new IllegalArgumentException("Agent cannot move into a cell occupied at phase start");
            }
        }

        for (Agent agent : moves.keySet()) {
            agentsByPosition.remove(agent.position(), agent);
        }

        for (Map.Entry<Agent, CellPosition> move : moves.entrySet()) {
            Agent agent = move.getKey();
            CellPosition target = move.getValue();

            agent.moveTo(target);
            agentsByPosition.put(target, agent);
        }


    }

    public void removeAgent(Agent agent) {
        Objects.requireNonNull(agent, "Agent must be defined");
        if (!agentsByPosition.remove(agent.position(), agent)) {
            throw new IllegalArgumentException("Agent must be placed on this grid");
        }
    }

    public void clearAgents() {
        agentsByPosition.clear();
    }

    public void clearResources() {
        for (int row = 0; row < sideLength; row++) {
            for (int column = 0; column < sideLength; column++) {
                resources[row][column] = false;
            }
        }
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

    public void regrowResources(
            double spawnProbability,
            RandomGenerator random
    ) {
        if (Double.isNaN(spawnProbability)
                || spawnProbability < 0.0
                || spawnProbability > 1.0) {
            throw new IllegalArgumentException(
                    "Resource spawn probability must be between 0 and 1"
            );
        }

        Objects.requireNonNull(random, "Random generator must be defined");

        for (int row = 0; row < sideLength; row++) {
            for (int column = 0; column < sideLength; column++) {
                if (!resources[row][column] && random.nextDouble() < spawnProbability) {
                    resources[row][column] = true;
                }
            }
        }
    }

    private void requireInsideGrid(CellPosition position) {
        Objects.requireNonNull(position, "Position must be defined");

        if (position.row() < 0 || position.row() >= sideLength
                || position.column() < 0 || position.column() >= sideLength) {
            throw new IllegalArgumentException("Position must be inside the grid");
        }
    }
}
