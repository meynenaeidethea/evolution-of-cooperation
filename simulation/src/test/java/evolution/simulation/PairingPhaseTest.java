package evolution.simulation;

import evolution.game.Action;
import evolution.game.Genotype;
import evolution.world.Agent;
import evolution.world.CellPosition;
import evolution.world.WorldGrid;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.random.RandomGeneratorFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PairingPhaseTest {

    private static final Genotype COOPERATE =
            new Genotype(Action.C, Action.C, Action.C, Action.C, Action.C);

    @Test
    void formsOnlyOnePairFromThreeMutualNeighbours() {
        WorldGrid grid = new WorldGrid(3);
        grid.placeAgent(new Agent(1, new CellPosition(0, 0), 1_000, COOPERATE));
        grid.placeAgent(new Agent(2, new CellPosition(0, 1), 1_000, COOPERATE));
        grid.placeAgent(new Agent(3, new CellPosition(1, 0), 1_000, COOPERATE));

        List<AgentPair> pairs = PairingPhase.formPairs(
                grid,
                RandomGeneratorFactory.of("L64X128MixRandom").create(1L)
        );

        assertEquals(1, pairs.size());
        assertEquals(2, new HashSet<>(List.of(pairs.getFirst().first(), pairs.getFirst().second())).size());
    }
}
