package evolution.world;

import evolution.game.Action;
import evolution.game.Genotype;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentTest {

    private static final Genotype ALWAYS_COOPERATE =
            new Genotype(Action.C, Action.C, Action.C, Action.C, Action.C);

    @Test
    void createsLivingAgentWithSpecifiedState() {
        CellPosition position = new CellPosition(2, 3);

        Agent agent = new Agent(7, position, 1_250, ALWAYS_COOPERATE);

        assertEquals(7, agent.agentId());
        assertEquals(position, agent.position());
        assertEquals(1_250, agent.energyHundredths());
        assertEquals(ALWAYS_COOPERATE, agent.genotype());
        assertTrue(agent.isAlive());
    }

    @Test
    void changesOnlyMutableAgentState() {
        Agent agent = new Agent(7, new CellPosition(0, 0), 500, ALWAYS_COOPERATE);

        agent.moveTo(new CellPosition(1, 2));
        agent.changeEnergyBy(-125);
        agent.die();

        assertEquals(7, agent.agentId());
        assertEquals(new CellPosition(1, 2), agent.position());
        assertEquals(375, agent.energyHundredths());
        assertEquals(ALWAYS_COOPERATE, agent.genotype());
        assertFalse(agent.isAlive());
    }

    @Test
    void rejectsNegativeAgentId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Agent(-1, new CellPosition(0, 0), 500, ALWAYS_COOPERATE)
        );
    }
}