package evolution.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class ActionTest {

    @Test
    void definesCooperateAndDefect() {
        assertArrayEquals(
                new Action[]{Action.C, Action.D},
                Action.values()
        );
    }
}
