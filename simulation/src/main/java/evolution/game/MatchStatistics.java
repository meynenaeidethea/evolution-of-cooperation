package evolution.game;

/** Outcomes are oriented from the first player's perspective. */
public record MatchStatistics(MatchPayoff payoff, int firstRoundCooperations,
                              int cooperations, int cc, int cd, int dc, int dd) {
    public int rounds() { return cc + cd + dc + dd; }
}
