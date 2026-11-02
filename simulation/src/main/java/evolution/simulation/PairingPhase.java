package evolution.simulation;

import evolution.world.Agent;
import evolution.world.WorldGrid;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.random.RandomGenerator;

public final class PairingPhase {

    private PairingPhase() {
    }

    public static List<AgentPair> formPairs(WorldGrid grid, RandomGenerator random, PairingMode mode) {
        Objects.requireNonNull(mode, "Pairing mode must be defined");
        if (mode == PairingMode.LOCAL) return formPairs(grid, random);
        Objects.requireNonNull(grid, "Grid must be defined");
        Objects.requireNonNull(random, "Random generator must be defined");
        // A uniform shuffled matching: each living agent plays at most once; one is left out if odd.
        int limit = mode == PairingMode.GLOBAL_MATCHED ? formPairs(grid, random).size() : Integer.MAX_VALUE;
        List<Agent> order = new ArrayList<>(grid.livingAgents());
        shuffle(order, random);
        List<AgentPair> pairs = new ArrayList<>();
        for (int i = 0; i + 1 < order.size() && pairs.size() < limit; i += 2) {
            Agent first = order.get(i);
            Agent second = order.get(i + 1);
            pairs.add(first.agentId() < second.agentId() ? new AgentPair(first, second) : new AgentPair(second, first));
        }
        return List.copyOf(pairs);
    }

    public static List<AgentPair> formPairs(WorldGrid grid, RandomGenerator random) {
        Objects.requireNonNull(grid, "Grid must be defined");
        Objects.requireNonNull(random, "Random generator must be defined");

        List<Agent> order = new ArrayList<>(grid.livingAgents());
        shuffle(order, random);
        Set<Agent> unpaired = new HashSet<>(order);
        List<AgentPair> pairs = new ArrayList<>();

        for (Agent agent : order) {
            if (!unpaired.contains(agent)) {
                continue;
            }

            List<Agent> candidates = grid.livingNeighbours(agent).stream()
                    .filter(unpaired::contains)
                    .toList();
            if (candidates.isEmpty()) {
                continue;
            }

            Agent partner = candidates.get(random.nextInt(candidates.size()));
            unpaired.remove(agent);
            unpaired.remove(partner);

            pairs.add(agent.agentId() < partner.agentId()
                    ? new AgentPair(agent, partner)
                    : new AgentPair(partner, agent));
        }

        return List.copyOf(pairs);
    }

    private static <T> void shuffle(List<T> values, RandomGenerator random) {
        for (int index = values.size() - 1; index > 0; index--) {
            int otherIndex = random.nextInt(index + 1);
            T value = values.get(index);
            values.set(index, values.get(otherIndex));
            values.set(otherIndex, value);
        }
    }
}
