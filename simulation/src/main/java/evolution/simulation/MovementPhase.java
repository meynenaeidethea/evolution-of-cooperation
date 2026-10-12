package evolution.simulation;

import evolution.world.Agent;
import evolution.world.CellPosition;
import evolution.world.WorldGrid;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class MovementPhase {

    private MovementPhase() {}

    public static List<MovementIntent> chooseIntents(
            WorldGrid grid,
            RandomGenerator random
    ) {
        Objects.requireNonNull(grid, "Grid must be defined");
        Objects.requireNonNull(random, "Random generator must be defined");

        List<MovementIntent> intents = new ArrayList<>();

        for (Agent agent : grid.livingAgents()) {
            List<CellPosition> targets = grid.availableMoveTargets(agent);
            CellPosition target = targets.get(random.nextInt(targets.size()));

            intents.add(new MovementIntent(agent, target));
        }

        return List.copyOf(intents);
    }

    public static List<MovementIntent> resolveAndApply(
            WorldGrid grid,
            List<MovementIntent> intents,
            RandomGenerator random
    ) {
        Objects.requireNonNull(grid, "Grid must be defined");
        Objects.requireNonNull(intents, "Intents must be defined");
        Objects.requireNonNull(random, "Random generator must be defined");

        Set<Agent> expectedAgents = new HashSet<>(grid.livingAgents());
        Set<Agent> agentsWithIntent = new HashSet<>();
        Map<CellPosition, List<MovementIntent>> contendersByTarget =
                new LinkedHashMap<>();

        for (MovementIntent intent : intents) {
            Objects.requireNonNull(intent, "Movement intent must be defined");

            if (!agentsWithIntent.add(intent.agent())) {
                throw new IllegalArgumentException(
                        "Each agent must have exactly one movement intent"
                );
            }

            contendersByTarget
                    .computeIfAbsent(intent.target(), ignored -> new ArrayList<>())
                    .add(intent);
        }

        if (!agentsWithIntent.equals(expectedAgents)) {
            throw new IllegalArgumentException(
                    "Each living agent must have exactly one movement intent"
            );
        }

        Map<Agent, CellPosition> resolvedMoves = new LinkedHashMap<>();
        List<MovementIntent> winners = new ArrayList<>();

        for (List<MovementIntent> contenders : contendersByTarget.values()) {
            MovementIntent winner = contenders.get(
                    random.nextInt(contenders.size())
            );

            winners.add(winner);
            resolvedMoves.put(winner.agent(), winner.target());
        }

        grid.moveAgents(resolvedMoves);

        return List.copyOf(winners);
    }
}