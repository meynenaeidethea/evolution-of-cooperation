package evolution.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PrisonersDilemmaTest {

    @Test
    void assignsPayoffsForAllActionPairs() {
        assertEquals(
                new RoundPayoff(3, 3),
                PrisonersDilemma.payoff(Action.C, Action.C)
        );
        assertEquals(
                new RoundPayoff(0, 5),
                PrisonersDilemma.payoff(Action.C, Action.D)
        );
        assertEquals(
                new RoundPayoff(5, 0),
                PrisonersDilemma.payoff(Action.D, Action.C)
        );
        assertEquals(
                new RoundPayoff(1, 1),
                PrisonersDilemma.payoff(Action.D, Action.D)
        );
    }
}