package evolution.game;

public class PrisonersDilemma {

    private PrisonersDilemma() {
    }

    public static RoundPayoff payoff(Action firstAction, Action secondAction){
        if (firstAction == null || secondAction == null) {
            throw new IllegalArgumentException("Actions must be defined");
        }

        return switch (firstAction) {
            case C -> secondAction == Action.C ? new RoundPayoff(3, 3) : new RoundPayoff(0, 5);
            case D -> secondAction == Action.C ? new RoundPayoff(5, 0) : new RoundPayoff(1, 1);
        };
    }
}
