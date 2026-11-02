package evolution.simulation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SimulationSessionTest {
    private SimulationConfig config(double spawn, int initial, int cost) {
        return new SimulationConfig(3, 4, 2, 1, initial, 400, cost,
                spawn, 0, 0.8, 0.01, 3, 2);
    }

    @Test
    void initialSnapshotIncludesResourcesUnderAgentsAndMetrics() {
        var snapshot = new SimulationSession(config(1, 1000, 0), 7).currentSnapshot();
        assertEquals(3, snapshot.sideLength());
        assertEquals(7, snapshot.seed());
        assertEquals(1, snapshot.generation());
        assertEquals(0, snapshot.tick());
        assertEquals(0, snapshot.elapsedTicks());
        assertEquals("running", snapshot.status());
        assertEquals(4, snapshot.agents().size());
        assertEquals(9, snapshot.resources().size());
        assertTrue(snapshot.agents().stream().allMatch(a -> snapshot.resources().contains(a.position())));
        assertEquals(4, snapshot.metrics().livingAgents());
        assertEquals(1000, snapshot.metrics().averageEnergyHundredths());
        assertEquals(9, snapshot.metrics().resourceCount());
        assertEquals(0, snapshot.metrics().resourceEnergyHundredths());
        assertEquals(0, snapshot.metrics().gamePayoff());
        assertEquals(0, snapshot.metrics().generationMetrics().gameStatistics().matches());
    }

    @Test
    void snapshotsAreDetachedAndDefensivelyCopyCollections() {
        var session = new SimulationSession(config(1, 1000, 0), 7);
        var before = session.currentSnapshot();
        var agents = new ArrayList<>(before.agents());
        var resources = new ArrayList<>(before.resources());
        var copy = new SimulationSnapshot(3, 7, 1, 0, 0, "running", agents, resources, before.metrics());
        agents.clear();
        resources.clear();
        session.advance();
        assertEquals(before, copy);
        assertEquals(1000, before.agents().getFirst().energyHundredths());
        assertEquals(1400, session.currentSnapshot().agents().getFirst().energyHundredths());
        assertThrows(UnsupportedOperationException.class, () -> before.agents().clear());
        assertThrows(UnsupportedOperationException.class, () -> before.resources().clear());
    }

    @Test
    void oneAdvanceIsOneTickAndBoundaryPreservesParentsBeforeReproduction() {
        var session = new SimulationSession(config(0, 1000, 0), 7);
        assertThrows(IllegalStateException.class, session::result);
        session.advance();
        assertEquals(1, session.currentSnapshot().tick());
        session.advance();
        var parents = session.currentSnapshot();
        assertEquals(1, parents.generation());
        assertEquals(2, parents.tick());
        session.advance();
        var children = session.currentSnapshot();
        assertEquals(2, children.generation());
        assertEquals(1, children.tick());
        assertEquals(3, children.elapsedTicks());
        assertTrue(children.agents().stream().allMatch(a -> a.agentId() > 4));
        session.advance();
        assertEquals("completed", session.currentSnapshot().status());
        assertEquals(2, session.result().generations().size());
        assertThrows(IllegalStateException.class, session::advance);
    }

    @Test
    void extinctionSnapshotHasNoDeadAgentsAndContainsDeathResources() {
        var session = new SimulationSession(config(0, 100, 100), 7);
        session.advance();
        var dead = session.currentSnapshot();
        assertTrue(dead.agents().isEmpty());
        assertEquals(4, dead.resources().size());
        assertEquals(0, dead.metrics().livingAgents());
        assertTrue(Double.isNaN(dead.metrics().averageEnergyHundredths()));
        assertEquals("extinct", dead.status());
        assertEquals(1, session.result().generations().size());
        assertThrows(IllegalStateException.class, session::advance);
    }

    @Test
    void preservesPreStepApiBatchResultsForFixedSeed() {
        var config = new SimulationConfig(3, 4, 2, 1, 1000, 400, 100,
                0.3, 20, 0.8, 0.01, 3, 2);
        var session = new SimulationSession(config, 7);
        while (!session.isFinished()) {
            session.advance();
        }
        var result = session.result();
        var first = result.generations().get(0);
        assertLegacyMetrics(first, 1, 1290, 1600, 18,
                Map.of("DCCCC", 1, "DDDDC", 1, "CDDDC", 1, "CDCDC", 1));
        var second = result.generations().get(1);
        assertLegacyMetrics(second, 2, 1340, 2000, 8,
                Map.of("DDDDC", 1, "DCCDC", 1, "DCCCC", 2));
        assertEquals(4, first.gameStatistics().matches());
        assertEquals(4, first.gameStatistics().rounds());
        assertEquals(0.5, first.gameStatistics().cooperationRate());
        assertEquals(4, second.gameStatistics().matches());
        assertEquals(4, second.gameStatistics().dd());
        assertEquals(0, second.gameStatistics().cooperationRate());
        assertEquals(result, new SimulationRunner().run(config, 7));
        var snapshot = session.currentSnapshot();
        assertEquals(2000, snapshot.metrics().resourceEnergyHundredths());
        assertEquals(8, snapshot.metrics().gamePayoff());
        assertEquals(1340, snapshot.metrics().averageEnergyHundredths());
        assertEquals(second, snapshot.metrics().generationMetrics());
    }

    private void assertLegacyMetrics(GenerationMetrics actual, int generation, double average,
                                     long resources, long payoff, Map<String, Integer> genotypes) {
        assertEquals(generation, actual.generation());
        assertEquals(4, actual.livingAgents());
        assertEquals(average, actual.averageEnergyHundredths());
        assertEquals(resources, actual.resourceEnergyHundredths());
        assertEquals(payoff, actual.gamePayoff());
        assertEquals(genotypes, actual.genotypeCounts());
    }
}
