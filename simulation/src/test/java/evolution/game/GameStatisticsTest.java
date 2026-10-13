package evolution.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GameStatisticsTest {
    private final Genotype cooperate = new Genotype(Action.C, Action.C, Action.C, Action.C, Action.C);
    private final Genotype defect = new Genotype(Action.D, Action.D, Action.D, Action.D, Action.D);

    @Test
    void cooperationAndDefectionHaveKnownCountsAndPayoffs() {
        var cc = RepeatedGame.playDetailed(cooperate, cooperate, 5);
        assertEquals(new MatchPayoff(15, 15), cc.payoff());
        assertEquals(new GameStatistics(1, 2, 5, 0, 0, 0), cc.statistics());
        assertEquals(1, cc.statistics().firstRoundCooperationRate());
        assertEquals(1, cc.statistics().cooperationRate());
        var dd = RepeatedGame.playDetailed(defect, defect, 5);
        assertEquals(new MatchPayoff(5, 5), dd.payoff());
        assertEquals(new GameStatistics(1, 0, 0, 0, 0, 5), dd.statistics());
        assertEquals(0, dd.statistics().cooperationRate());
    }

    @Test
    void asymmetricResultsKeepBothPlayersPerspectives() {
        var cd = RepeatedGame.playDetailed(cooperate, defect, 3);
        assertEquals(new GameStatistics(1, 1, 0, 3, 0, 0), cd.statistics());
        assertEquals(0.5, cd.statistics().firstRoundCooperationRate());
        assertEquals(0.5, cd.statistics().cooperationRate());
        var dc = RepeatedGame.playDetailed(defect, cooperate, 3);
        assertEquals(new GameStatistics(1, 1, 0, 0, 3, 0), dc.statistics());
        assertEquals(new MatchPayoff(15, 0), dc.payoff());
    }

    @Test
    void distinguishesFirstRoundCooperationFromAllRounds() {
        var titForTat = new Genotype(Action.C, Action.C, Action.D, Action.C, Action.D);
        var match = RepeatedGame.playDetailed(titForTat, defect, 3);
        assertEquals(new MatchPayoff(2, 7), match.payoff());
        assertEquals(new GameStatistics(1, 1, 0, 1, 0, 2), match.statistics());
        assertEquals(0.5, match.statistics().firstRoundCooperationRate());
        assertEquals(1.0 / 6, match.statistics().cooperationRate());
        assertEquals(1.0 / 3, match.statistics().cdFraction());
        assertEquals(2.0 / 3, match.statistics().ddFraction());
    }

    @Test
    void accumulatesUsingLongCountsAndEmptyRatiosAreUndefined() {
        var large = new GameStatistics(3_000_000_000L, 3_000_000_000L, 0, 3_000_000_000L, 0, 0);
        var sum = large.plus(large);
        assertEquals(6_000_000_000L, sum.matches());
        assertEquals(12_000_000_000L, sum.actions());
        assertEquals(0.5, sum.cooperationRate());
        assertEquals(large, GameStatistics.EMPTY.plus(large));
        assertTrue(Double.isNaN(GameStatistics.EMPTY.cooperationRate()));
        assertTrue(Double.isNaN(GameStatistics.EMPTY.firstRoundCooperationRate()));
        assertTrue(Double.isNaN(GameStatistics.EMPTY.ccFraction()));
        assertTrue(Double.isNaN(GameStatistics.EMPTY.cdFraction()));
        assertTrue(Double.isNaN(GameStatistics.EMPTY.dcFraction()));
        assertTrue(Double.isNaN(GameStatistics.EMPTY.ddFraction()));
    }
}
