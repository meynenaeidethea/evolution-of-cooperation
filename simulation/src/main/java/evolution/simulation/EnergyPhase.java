package evolution.simulation;

import evolution.world.Agent;
import evolution.world.WorldGrid;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class EnergyPhase {

    private EnergyPhase() {
    }

    public static Map<Agent, Integer> collectResourceEnergy(
            WorldGrid grid,
            int resourceEnergyHundredths
    ) {
        Objects.requireNonNull(grid, "Grid must be defined");
        if (resourceEnergyHundredths < 0) {
            throw new IllegalArgumentException("Resource energy must not be negative");
        }

        Map<Agent, Integer> gains = new LinkedHashMap<>();
        for (Agent agent : grid.livingAgents()) {
            int gain = grid.takeResourceAt(agent.position())
                    ? resourceEnergyHundredths : 0;
            gains.put(agent, gain);
        }
        return Map.copyOf(gains);
    }

    public static List<Agent> applyEnergyAndRemoveDead(
            WorldGrid grid,
            Map<Agent, Integer> resourceGains,
            Map<Agent, Integer> gamePayoffs,
            SimulationConfig config
    ) {
        Objects.requireNonNull(grid, "Grid must be defined");
        Objects.requireNonNull(resourceGains, "Resource gains must be defined");
        Objects.requireNonNull(gamePayoffs, "Game payoffs must be defined");
        Objects.requireNonNull(config, "Configuration must be defined");

        List<Agent> dead = new java.util.ArrayList<>();
        for (Agent agent : grid.livingAgents()) {
            int resourceGain = resourceGains.getOrDefault(agent, 0);
            int gamePayoff = gamePayoffs.getOrDefault(agent, 0);
            int change = resourceGain
                    + config.gamePayoffWeightHundredths() * gamePayoff
                    - config.livingCostHundredths();
            agent.changeEnergyBy(change);
            if (agent.energyHundredths() <= 0) {
                agent.die();
                dead.add(agent);
            }
        }

        for (Agent agent : dead) {
            grid.removeAgent(agent);
            grid.placeResource(agent.position());
        }
        return List.copyOf(dead);
    }
}
