package evolution.simulation;

import evolution.game.*;
import evolution.world.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.random.RandomGeneratorFactory;
import static org.junit.jupiter.api.Assertions.*;

class StudyTest {
    @TempDir Path temporary;
    private java.util.random.RandomGenerator random() {
        return RandomGeneratorFactory.of("L64X128MixRandom").create(11);
    }
    private Agent agent(long id, int row, int col, int energy, int genotype) {
        return new Agent(id, new CellPosition(row, col), energy, Genotype.fromIndex(genotype));
    }
    @Test void countsActionsAndOrientedOutcomes() {
        var match = RepeatedGame.analyze(Genotype.fromIndex(0), Genotype.fromIndex(31), 5);
        assertEquals(new MatchPayoff(0, 25), match.payoff());
        assertEquals(1, match.firstRoundCooperations());
        assertEquals(5, match.cooperations());
        assertEquals(5, match.cd());
        assertEquals(0, match.dc());
        var reverse = RepeatedGame.analyze(Genotype.fromIndex(31), Genotype.fromIndex(0), 5);
        assertEquals(match.cd(), reverse.dc());
    }
    @Test void genotypeIndexRoundTripsEveryStrategy() {
        for (int i = 0; i < 32; i++) assertEquals(i, Genotype.fromIndex(i).index());
    }
    @Test void globalPairsDistantAgentsOnlyOnceAndOrdersById() {
        var grid = new WorldGrid(10);
        grid.placeAgent(agent(3, 0, 0, 1000, 0));
        grid.placeAgent(agent(2, 4, 4, 1000, 0));
        grid.placeAgent(agent(1, 7, 7, 1000, 0));
        assertTrue(PairingPhase.formPairs(grid, random()).isEmpty());
        var pairs = PairingPhase.formPairs(grid, random(), PairingMode.GLOBAL);
        assertEquals(1, pairs.size());
        assertTrue(pairs.getFirst().first().agentId() < pairs.getFirst().second().agentId());
    }
    @Test void countsToroidalNeighbourPairsOnce() {
        var grid = new WorldGrid(3);
        grid.placeAgent(agent(1, 0, 0, 100, 0));
        grid.placeAgent(agent(2, 2, 2, 100, 0));
        grid.placeAgent(agent(3, 0, 2, 100, 31));
        assertEquals(1.0 / 3, SimulationRunner.clustering(grid));
        grid.clearAgents();
        assertTrue(Double.isNaN(SimulationRunner.clustering(grid)));
    }
    @Test void loneParentAndExtremeMutation() {
        var parent = agent(1, 0, 0, 100, 0);
        assertSame(parent, Genetics.selectTournament(List.of(parent), 3, random()));
        for (double mutation : new double[]{0, 1}) {
            var c = SimulationConfig.baseline().with(5, 20, mutation, .1, 400, PairingMode.LOCAL, 1);
            assertEquals(Genotype.fromIndex(mutation == 0 ? 0 : 31),
                    Genetics.childGenotype(parent.genotype(), parent.genotype(), c, random()));
        }
        var rich = agent(2, 1, 1, 200, 31);
        for (int i = 0; i < 50; i++) {
            assertSame(rich, Genetics.selectTournament(List.of(parent, rich), 3, random()));
        }
    }
    @Test void extinctionStopsAndLeavesUndefinedStatisticsEmpty() throws Exception {
        var c = new SimulationConfig(3, 1, 10, 1, 100, 0, 100, 0, 0, 0, 0, 3, 5);
        var result = new SimulationRunner().run(c, 1);
        assertEquals("extinct", result.status());
        assertEquals(1, result.generations().size());
        var m = result.generations().getFirst();
        assertEquals(1, m.ticks());
        assertEquals(0, m.survivalFraction());
        assertTrue(Double.isNaN(m.averageEnergyHundredths()));
        assertTrue(Double.isNaN(m.cooperationFraction()));
        CsvResultsWriter.write(temporary, List.of(result));
        var csv = Files.readAllLines(temporary.resolve("genotype-frequencies.csv"));
        assertEquals(33, csv.size());
        assertTrue(csv.get(1).endsWith(",0,"));
        assertFalse(Files.readString(temporary.resolve("generations.csv")).contains("NaN"));
    }
    @Test void repeatedCsvIsByteIdenticalAndSnapshotsDoNotChangeResults() throws Exception {
        var c = new SimulationConfig(5, 10, 5, 3, 1000, 400, 100, .1, 20, .8, .01, 3, 3);
        var runner = new SimulationRunner();
        var first = runner.run(c, 4);
        var second = runner.run("baseline", 1, c, 4, (g, grid) -> {
            try { WorldSnapshotWriter.write(temporary.resolve("snapshots/" + g + ".svg"), g, grid); }
            catch (Exception e) { throw new RuntimeException(e); }
        });
        assertEquals(first, second);
        CsvResultsWriter.write(temporary.resolve("a"), List.of(first));
        CsvResultsWriter.write(temporary.resolve("b"), List.of(second));
        for (String name : List.of("runs.csv", "generations.csv", "genotype-frequencies.csv"))
            assertArrayEquals(Files.readAllBytes(temporary.resolve("a/" + name)), Files.readAllBytes(temporary.resolve("b/" + name)));
        for (var m : first.generations()) {
            assertEquals(m.rounds(), m.cc() + m.cd() + m.dc() + m.dd());
            assertEquals(m.cooperations(), 2 * m.cc() + m.cd() + m.dc());
            assertEquals(m.matches() * c.roundsPerMatch(), m.rounds());
            assertEquals(m.livingAgents(), m.genotypeCounts().values().stream().mapToInt(Integer::intValue).sum());
        }
    }
    @Test void allScenariosChangeOneFactor() {
        var scenarios = ExperimentScenarios.all(100);
        assertEquals(18, scenarios.size());
        var base = scenarios.get("baseline");
        for (var entry : scenarios.entrySet()) {
            if (entry.getKey().equals("baseline")) continue;
            var c = entry.getValue();
            int differences = (c.roundsPerMatch() != base.roundsPerMatch() ? 1 : 0)
                    + (c.gamePayoffWeightHundredths() != base.gamePayoffWeightHundredths() ? 1 : 0)
                    + (c.mutationProbability() != base.mutationProbability() ? 1 : 0)
                    + (c.resourceSpawnProbability() != base.resourceSpawnProbability() ? 1 : 0)
                    + (c.resourceEnergyHundredths() != base.resourceEnergyHundredths() ? 1 : 0)
                    + (c.pairingMode() != base.pairingMode() ? 1 : 0);
            assertEquals(1, differences, entry.getKey());
        }
    }
}
