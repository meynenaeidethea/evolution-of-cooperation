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
