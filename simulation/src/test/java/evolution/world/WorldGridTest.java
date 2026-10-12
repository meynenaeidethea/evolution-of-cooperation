package evolution.world;

import evolution.game.Action;
import evolution.game.Genotype;
import org.junit.jupiter.api.Test;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldGridTest {

    private static final Genotype ALWAYS_COOPERATE =
            new Genotype(Action.C, Action.C, Action.C, Action.C, Action.C);

    @Test
    void createsGridWithSpecifiedSideLength() {
        WorldGrid grid = new WorldGrid(3);

        assertEquals(3, grid.sideLength());
    }

    @Test
    void rejectsGridSmallerThanSpecificationAllows() {
        assertThrows(IllegalArgumentException.class, () -> new WorldGrid(2));
    }

    @Test
    void allowsOnlyOneAgentInCell() {
        WorldGrid grid = new WorldGrid(3);
        CellPosition position = new CellPosition(1, 1);

        Agent firstAgent = new Agent(1, position, 1_000, ALWAYS_COOPERATE);
        Agent secondAgent = new Agent(2, position, 1_000, ALWAYS_COOPERATE);

        grid.placeAgent(firstAgent);

        assertTrue(grid.agentAt(position).isPresent());
        assertEquals(firstAgent, grid.agentAt(position).orElseThrow());

        assertThrows(
                IllegalArgumentException.class,
                () -> grid.placeAgent(secondAgent)
        );
    }

    @Test
    void resourceCanExistUnderAgentAndIsRemovedWhenTaken() {
        WorldGrid grid = new WorldGrid(3);
        CellPosition position = new CellPosition(1, 1);
        Agent agent = new Agent(1, position, 1_000, ALWAYS_COOPERATE);

        grid.placeAgent(agent);
        grid.placeResource(position);

        assertTrue(grid.hasResourceAt(position));
        assertTrue(grid.takeResourceAt(position));
        assertFalse(grid.hasResourceAt(position));
        assertFalse(grid.takeResourceAt(position));
    }

    @Test
    void rejectsPositionOutsideGrid() {
        WorldGrid grid = new WorldGrid(3);

        assertThrows(
                IllegalArgumentException.class,
                () -> grid.placeResource(new CellPosition(3, 0))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> grid.placeResource(new CellPosition(0, 3))
        );
    }

    @Test
    void regrowsResourceOnEveryEmptyCellWhenProbabilityIsOne() {
        WorldGrid grid = new WorldGrid(3);
        CellPosition agentPosition = new CellPosition(1, 1);
        grid.placeAgent(new Agent(1, agentPosition, 1_000, ALWAYS_COOPERATE));

        grid.regrowResources(1.0, randomWithSeed(1));

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                assertTrue(grid.hasResourceAt(new CellPosition(row, column)));
            }
        }
    }

    @Test
    void doesNotRegrowNewResourcesWhenProbabilityIsZero() {
        WorldGrid grid = new WorldGrid(3);
        CellPosition existingResource = new CellPosition(0, 0);

        grid.placeResource(existingResource);
        grid.regrowResources(0.0, randomWithSeed(1));

        assertTrue(grid.hasResourceAt(existingResource));
        assertFalse(grid.hasResourceAt(new CellPosition(1, 1)));
    }

    @Test
    void rejectsInvalidResourceSpawnProbability() {
        WorldGrid grid = new WorldGrid(3);

        assertThrows(
                IllegalArgumentException.class,
                () -> grid.regrowResources(-0.01, randomWithSeed(1))
        );
    }

    @Test
    void offersOnlyFreeToroidalNeighboursAndStayingInPlace() {
        WorldGrid grid = new WorldGrid(3);
        Agent agent = new Agent(
                1,
                new CellPosition(0, 0),
                1_000,
                ALWAYS_COOPERATE
        );
        Agent blockingAgent = new Agent(
                2,
                new CellPosition(2, 2),
                1_000,
                ALWAYS_COOPERATE
        );

        grid.placeAgent(agent);
        grid.placeAgent(blockingAgent);

        var targets = grid.availableMoveTargets(agent);

        assertTrue(targets.contains(new CellPosition(0, 0)));
        assertFalse(targets.contains(new CellPosition(2, 2)));
        assertEquals(8, targets.size());
    }

    private RandomGenerator randomWithSeed(long seed) {
        return RandomGeneratorFactory
                .of("L64X128MixRandom")
                .create(seed);
    }
}