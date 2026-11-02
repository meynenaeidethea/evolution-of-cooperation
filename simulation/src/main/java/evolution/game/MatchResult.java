package evolution.game;

/** Conditional-gene exposures from both players, measured during the same game as the payoff. */
public record MatchResult(MatchPayoff payoff, GameStatistics statistics,
                          long afterCCUses, long afterCDUses, long afterDCUses, long afterDDUses) {
    public MatchResult(MatchPayoff payoff, GameStatistics statistics) {
        this(payoff, statistics, 0, 0, 0, 0);
    }
}
