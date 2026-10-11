package evolution.world;

import evolution.game.Action;
import evolution.game.Genotype;
import org.junit.jupiter.api.Test;

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
    }
}