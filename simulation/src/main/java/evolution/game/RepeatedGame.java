package evolution.game;

public class RepeatedGame {

    private RepeatedGame() {}

    public static MatchPayoff play(
            Genotype firstGenotype,
            Genotype secondGenotype,
            int rounds
    ) {
        return playDetailed(firstGenotype, secondGenotype, rounds).payoff();
    }

    /** First and second are caller-defined; the simulation passes the lower agent ID first. */
    public static MatchResult playDetailed(Genotype firstGenotype, Genotype secondGenotype, int rounds) {
        if (firstGenotype == null || secondGenotype == null) {
            throw new IllegalArgumentException("Genotype must be defined");
        }
        if (rounds < 1) {
            throw new IllegalArgumentException("Rounds must be at least 1");
        }

        Action firstAction = firstGenotype.firstMove();
        Action secondAction = secondGenotype.firstMove();
        int firstTotal = 0;
        int secondTotal = 0;
        long firstCooperation = (firstAction == Action.C ? 1 : 0) + (secondAction == Action.C ? 1 : 0);
        long cc = 0;
        long cd = 0;
        long dc = 0;
        long dd = 0;

        for (int round = 0; round < rounds; round++) {
            RoundPayoff payoff = PrisonersDilemma.payoff(firstAction, secondAction);
            firstTotal += payoff.firstAgent();
            secondTotal += payoff.secondAgent();
            if (firstAction == Action.C) {
                if (secondAction == Action.C) cc++; else cd++;
            } else {
                if (secondAction == Action.C) dc++; else dd++;
            }

            if (round < rounds - 1) {
                Action nextFirstAction = firstGenotype.actionAfter(firstAction, secondAction);
                Action nextSecondAction = secondGenotype.actionAfter(secondAction, firstAction);
                firstAction = nextFirstAction;
                secondAction = nextSecondAction;
            }
        }

        return new MatchResult(new MatchPayoff(firstTotal, secondTotal),
                new GameStatistics(1, firstCooperation, cc, cd, dc, dd));
    }
}
