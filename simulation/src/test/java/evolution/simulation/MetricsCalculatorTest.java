package evolution.simulation;

import evolution.game.Action;
import evolution.game.GameStatistics;
import evolution.game.Genotype;
import evolution.world.Agent;
import evolution.world.CellPosition;
import evolution.world.WorldGrid;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class MetricsCalculatorTest {
    private final Genotype cooperate = new Genotype(Action.C, Action.C, Action.C, Action.C, Action.C);
    private final Genotype defect = new Genotype(Action.D, Action.D, Action.D, Action.D, Action.D);
    private final SimulationConfig config = new SimulationConfig(4, 4, 2, 3, 1000, 400, 100,
            0.1, 20, 0.8, 0.01, 3, 2);

    @Test
    void measuresSurvivorsAndNormalizesEnergyByInitialPopulation() {
        var grid = new WorldGrid(4);
        grid.placeAgent(new Agent(1, new CellPosition(0, 0), 1000, cooperate));
        grid.placeAgent(new Agent(2, new CellPosition(0, 3), 2000, cooperate));
        grid.placeAgent(new Agent(3, new CellPosition(3, 0), 600, defect));
        var metrics = MetricsCalculator.measure(2, 2, grid, config, 1600, 15,
                new GameStatistics(1, 1, 0, 3, 0, 0));
        assertEquals(3, metrics.livingAgents());
        assertEquals(0.75, metrics.survivalFraction());
        assertEquals(1200, metrics.averageEnergyHundredths());
        assertEquals(2000, metrics.maximumEnergyHundredths());
        assertEquals(400, metrics.averageResourceEnergyHundredths());
        assertEquals(3.75, metrics.averageGamePayoff());
        assertEquals(300, metrics.gameEnergyHundredths());
        assertEquals(1.0 / 3, metrics.spatialClustering());
        assertEquals(Map.of("CCCCC", 2, "DDDDD", 1), metrics.genotypeCounts());
        assertFalse(metrics.extinct());
        assertThrows(UnsupportedOperationException.class, () -> metrics.genotypeCounts().clear());
        assertEquals(3, grid.livingAgents().size());
    }

    @Test
    void clusteringCountsEachToroidalMoorePairOnceAtMinimumSideLength() {
        var grid = new WorldGrid(3);
        long id = 0;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                grid.placeAgent(new Agent(id++, new CellPosition(row, column), 1000,
                        row == 0 && column == 0 ? defect : cooperate));
            }
        }
        // 36 unordered pairs, of which 8 connect to the single different genotype.
        assertEquals(28.0 / 36, MetricsCalculator.spatialClustering(grid));
    }

    @Test
    void clusteringHandlesUniformDifferentAndIsolatedNeighbours() {
        var grid = new WorldGrid(4);
        var first = new Agent(1, new CellPosition(0, 0), 1000, cooperate);
        grid.placeAgent(first);
        assertTrue(Double.isNaN(MetricsCalculator.spatialClustering(grid)));
        var second = new Agent(2, new CellPosition(3, 3), 1000, cooperate);
        grid.placeAgent(second);
        assertEquals(1, MetricsCalculator.spatialClustering(grid));
        grid.removeAgent(second);
        grid.placeAgent(new Agent(3, new CellPosition(3, 3), 1000, defect));
        assertEquals(0, MetricsCalculator.spatialClustering(grid));
    }

    @Test
    void extinctionLeavesUndefinedSurvivorMetricsButKeepsPlayedGames() {
        var metrics = MetricsCalculator.measure(1, 1, new WorldGrid(4), config, 0, 15,
                new GameStatistics(1, 1, 0, 3, 0, 0));
        assertTrue(metrics.extinct());
        assertEquals(0, metrics.survivalFraction());
        assertTrue(Double.isNaN(metrics.averageEnergyHundredths()));
        assertTrue(Double.isNaN(metrics.maximumEnergyHundredths()));
        assertTrue(Double.isNaN(metrics.spatialClustering()));
        assertEquals(3, metrics.gameStatistics().rounds());
        assertEquals(0.5, metrics.gameStatistics().cooperationRate());
    }

    @Test
    void pairsOrientAsymmetricOutcomesByLowerAgentId() {
        var grid = new WorldGrid(4);
        var highId = new Agent(20, new CellPosition(0, 0), 1000, cooperate);
        var lowId = new Agent(5, new CellPosition(0, 1), 1000, defect);
        grid.placeAgent(highId);
        grid.placeAgent(lowId);
        for (int seed = 0; seed < 10; seed++) {
            var result = new SimulationRunner().playPairs(grid, config, new Random(seed));
            assertEquals(new GameStatistics(1, 1, 0, 0, 3, 0), result.statistics());
            assertEquals(15, result.payoffs().get(lowId));
            assertEquals(0, result.payoffs().get(highId));
        }
    }

    @Test
    void aggregateEnergyDoesNotOverflowInt() {
        long total = 3_000_000_000L;
        var metrics = MetricsCalculator.measure(1, 1, new WorldGrid(4), config, total, total, GameStatistics.EMPTY);
        assertEquals(total, metrics.resourceEnergyHundredths());
        assertEquals(60_000_000_000L, metrics.gameEnergyHundredths());
    }
}
