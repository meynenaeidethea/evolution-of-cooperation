package evolution.game;

public class RepeatedGame {

    private RepeatedGame() {}

    public static MatchPayoff play(
            Genotype firstGenotype,
            Genotype secondGenotype,
            int rounds
    ) {
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

        for (int round = 0; round < rounds; round++) {
            RoundPayoff payoff = PrisonersDilemma.payoff(firstAction, secondAction);
            firstTotal += payoff.firstAgent();
            secondTotal += payoff.secondAgent();

            if (round < rounds - 1) {
                Action nextFirstAction = firstGenotype.actionAfter(firstAction, secondAction);
                Action nextSecondAction = secondGenotype.actionAfter(secondAction, firstAction);
                firstAction = nextFirstAction;
                secondAction = nextSecondAction;
            }
        }

        return new MatchPayoff(firstTotal, secondTotal);
    }
}
