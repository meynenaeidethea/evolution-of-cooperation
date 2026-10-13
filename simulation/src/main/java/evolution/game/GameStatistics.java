package evolution.game;

/** Counts include all played rounds, even for agents that die later in the tick. */
public record GameStatistics(long matches, long firstRoundCooperativeActions,
                             long cc, long cd, long dc, long dd) {
    public static final GameStatistics EMPTY = new GameStatistics(0, 0, 0, 0, 0, 0);

    public GameStatistics plus(GameStatistics other) {
        return new GameStatistics(matches + other.matches, firstRoundCooperativeActions
                + other.firstRoundCooperativeActions, cc + other.cc, cd + other.cd,
                dc + other.dc, dd + other.dd);
    }

    public long rounds() {
        return cc + cd + dc + dd;
    }

    public long firstRoundActions() {
        return 2 * matches;
    }

    public long actions() {
        return 2 * rounds();
    }

    public long cooperativeActions() {
        return 2 * cc + cd + dc;
    }

    public double firstRoundCooperationRate() {
        return fraction(firstRoundCooperativeActions, firstRoundActions());
    }

    public double cooperationRate() {
        return fraction(cooperativeActions(), actions());
    }

    public double ccFraction() { return fraction(cc, rounds()); }
    public double cdFraction() { return fraction(cd, rounds()); }
    public double dcFraction() { return fraction(dc, rounds()); }
    public double ddFraction() { return fraction(dd, rounds()); }

    private double fraction(long numerator, long denominator) {
        return denominator == 0 ? Double.NaN : (double) numerator / denominator;
    }
}
