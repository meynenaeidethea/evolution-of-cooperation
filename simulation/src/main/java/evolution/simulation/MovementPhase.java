package evolution.simulation;

import evolution.world.Agent;
import evolution.world.CellPosition;
import evolution.world.WorldGrid;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

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
}