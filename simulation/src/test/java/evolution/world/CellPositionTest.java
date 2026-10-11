package evolution.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CellPositionTest {

    @Test
    void movesInsideGridWithoutWrapping() {
        CellPosition position = new CellPosition(1, 1);

        assertEquals(
                new CellPosition(2, 0),
                position.moveBy(1, -1, 3)
        );
    }

    @Test
    void wrapsAcrossAllToroidalBorders() {
        CellPosition position = new CellPosition(0, 0);

        assertEquals(
                new CellPosition(2, 2),
                position.moveBy(-1, -1, 3)
        );
        assertEquals(
                new CellPosition(0, 0),
                new CellPosition(2, 2).moveBy(1, 1, 3)
        );
    }

    @Test
    void rejectsNonPositiveSideLength() {
        CellPosition position = new CellPosition(0, 0);

        assertThrows(
                IllegalArgumentException.class,
                () -> position.moveBy(0, 0, 0)
        );
    }
}