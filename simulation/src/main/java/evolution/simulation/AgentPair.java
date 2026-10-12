package evolution.simulation;

import evolution.world.Agent;

import java.util.Objects;

public record AgentPair(Agent first, Agent second) {

    public AgentPair {
        Objects.requireNonNull(first, "First agent must be defined");
        Objects.requireNonNull(second, "Second agent must be defined");
        if (first == second) {
            throw new IllegalArgumentException("A pair must contain two different agents");
        }
    }
}
