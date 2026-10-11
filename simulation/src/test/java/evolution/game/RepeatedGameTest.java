package evolution.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RepeatedGameTest {

    private static final Genotype ALWAYS_COOPERATE =
            new Genotype(Action.C, Action.C, Action.C, Action.C, Action.C);

    private static final Genotype ALWAYS_DEFECT =
            new Genotype(Action.D, Action.D, Action.D, Action.D, Action.D);

    @Test
    void repeatsActionsAndAccumulatesPayoffs() {
        assertEquals(
                new MatchPayoff(0, 15),
                RepeatedGame.play(ALWAYS_COOPERATE, ALWAYS_DEFECT, 3)
        );
    }

    @Test
    void usesPreviousRoundFromEachAgentsPerspective() {
        Genotype titForTat =
                new Genotype(Action.C, Action.C, Action.D, Action.C, Action.D);

        assertEquals(
                new MatchPayoff(2, 7),
                RepeatedGame.play(titForTat, ALWAYS_DEFECT, 3)
        );
    }

    @Test
    void rejectsZeroRounds() {
        assertThrows(
                IllegalArgumentException.class,
                () -> RepeatedGame.play(ALWAYS_COOPERATE, ALWAYS_DEFECT, 0)
        );
    }
}