package evolution.simulation;

import evolution.game.Action;
import evolution.game.Genotype;
import evolution.world.Agent;
import evolution.world.CellPosition;
import evolution.world.WorldGrid;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.random.RandomGeneratorFactory;

import static org.junit.jupiter.api.Assertions.*;

class GlobalPairingTest {
    private static final Genotype COOPERATE = new Genotype(Action.C, Action.C, Action.C, Action.C, Action.C);

    private java.util.random.RandomGenerator random(long seed) {
        return RandomGeneratorFactory.of(SimulationSession.RANDOM_GENERATOR).create(seed);
    }

    @Test
    void globalPartnersNeedNotBeNeighboursAndLocalModeIsUnchanged() {
        var grid = new WorldGrid(10);
        grid.placeAgent(new Agent(1, new CellPosition(0, 0), 1000, COOPERATE));
        grid.placeAgent(new Agent(2, new CellPosition(5, 5), 1000, COOPERATE));
        assertTrue(PairingPhase.formPairs(grid, random(1)).isEmpty());
        assertEquals(PairingPhase.formPairs(grid, random(1)), PairingPhase.formPairs(grid, random(1), PairingMode.LOCAL));
        var pairs = PairingPhase.formPairs(grid, random(1), PairingMode.GLOBAL);
        assertEquals(1, pairs.size());
        assertEquals(1, pairs.getFirst().first().agentId());
        assertEquals(2, pairs.getFirst().second().agentId());
        assertThrows(UnsupportedOperationException.class, pairs::clear);
    }

    @Test
    void excludesDeadAgentsPairsEachLivingAgentAtMostOnceAndReproducesSeed() {
        var grid = new WorldGrid(10);
        for (int i = 0; i < 8; i++) {
            var agent = new Agent(i + 1, new CellPosition(i, i), 1000, COOPERATE);
            if (i == 7) agent.die();
            grid.placeAgent(agent);
        }
        var pairs = PairingPhase.formPairs(grid, random(7), PairingMode.GLOBAL);
        assertEquals(pairs, PairingPhase.formPairs(grid, random(7), PairingMode.GLOBAL));
        assertEquals(3, pairs.size());
        var ids = new HashSet<Long>();
        for (var pair : pairs) {
            assertTrue(pair.first().isAlive()); assertTrue(pair.second().isAlive());
            assertTrue(pair.first().agentId() < pair.second().agentId());
            assertTrue(ids.add(pair.first().agentId())); assertTrue(ids.add(pair.second().agentId()));
        }
        assertFalse(ids.contains(8L));
        assertTrue(PairingPhase.formPairs(new WorldGrid(3), random(1), PairingMode.GLOBAL).isEmpty());
    }

    @Test
    void batchAndStepRunAgreeAndDefaultRemainsLocal() {
        var config = new SimulationConfig(10, 6, 3, 2, 1000, 400, 0, .1, 20, .8, .01, 3, 2);
        var runner = new SimulationRunner();
        assertEquals(runner.run(config, 7), runner.run(config, 7, PairingMode.LOCAL));
        var session = new SimulationSession(config, 7, PairingMode.GLOBAL);
        while (!session.isFinished()) session.advance();
        var result = runner.run(config, 7, PairingMode.GLOBAL);
        assertEquals(result, session.result());
        assertSame(config, result.config());
        assertEquals(PairingMode.GLOBAL, result.pairingMode());
        assertEquals(9, result.generations().getFirst().gameStatistics().matches());
        assertEquals(9, result.generations().getLast().gameStatistics().matches());
    }
}
