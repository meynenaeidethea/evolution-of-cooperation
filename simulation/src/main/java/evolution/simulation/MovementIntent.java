package evolution.simulation;

import evolution.world.Agent;
import evolution.world.CellPosition;

import java.util.Objects;

public record MovementIntent(Agent agent, CellPosition target) {

    public MovementIntent {
        Objects.requireNonNull(agent, "Agent must be defined");
        Objects.requireNonNull(target, "Target must be defined");
    }
}
