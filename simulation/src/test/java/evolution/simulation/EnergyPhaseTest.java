package evolution.simulation;

import evolution.game.Action;
import evolution.game.Genotype;
import evolution.world.Agent;
import evolution.world.CellPosition;
import evolution.world.WorldGrid;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnergyPhaseTest {

    private static final Genotype COOPERATE =
            new Genotype(Action.C, Action.C, Action.C, Action.C, Action.C);

    @Test
    void collectsResourceBeforeEnergyIsApplied() {
        WorldGrid grid = new WorldGrid(3);
        Agent agent = new Agent(1, new CellPosition(1, 1), 100, COOPERATE);
        grid.placeAgent(agent);
        grid.placeResource(agent.position());

        Map<Agent, Integer> gains = EnergyPhase.collectResourceEnergy(grid, 400);

        assertEquals(400, gains.get(agent));
        assertEquals(100, agent.energyHundredths());
        assertFalse(grid.hasResourceAt(agent.position()));
    }

    @Test
    void removesDeadAgentAndLeavesResourceOnItsCell() {
        WorldGrid grid = new WorldGrid(3);
        CellPosition position = new CellPosition(1, 1);
        Agent agent = new Agent(1, position, 100, COOPERATE);
        grid.placeAgent(agent);
        SimulationConfig config = new SimulationConfig(
                3, 1, 1, 1, 100, 0, 100,
                0.0, 0, 0.0, 0.0, 1, 1
        );

        var dead = EnergyPhase.applyEnergyAndRemoveDead(
                grid, Map.of(agent, 0), Map.of(agent, 0), config
        );

        assertEquals(List.of(agent), dead);
        assertFalse(agent.isAlive());
        assertTrue(grid.agentAt(position).isEmpty());
        assertTrue(grid.hasResourceAt(position));
    }
}
