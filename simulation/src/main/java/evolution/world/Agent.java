package evolution.world;

import evolution.game.Genotype;

import java.util.Objects;

public final class Agent {

    private final long agentId;
    private CellPosition position;
    private int energyHundredths;
    private final Genotype genotype;
    private boolean alive;

    public Agent(
            long agentId,
            CellPosition position,
            int energyHundredths,
            Genotype genotype
    ) {
        if (agentId < 0) {
            throw new IllegalArgumentException("Agent ID must not be negative");
        }

        this.agentId = agentId;
        this.position = Objects.requireNonNull(position, "Position must be defined");
        this.energyHundredths =  energyHundredths;
        this.genotype = Objects.requireNonNull(genotype, "Genotype must be defined");
        this.alive = true;
    }

    public long agentId() {
        return agentId;
    }

    public CellPosition position() {
        return position;
    }

    public int energyHundredths() {
        return energyHundredths;
    }

    public Genotype genotype() {
        return genotype;
    }

    public boolean isAlive() {
        return alive;
    }

    public void moveTo(CellPosition newPosition) {
        position = Objects.requireNonNull(newPosition, "Position must be defined");
    }

    public void changeEnergyBy(int changeHundredths) {
        energyHundredths += changeHundredths;
    }

    public void die() {
        alive = false;
    }
}
