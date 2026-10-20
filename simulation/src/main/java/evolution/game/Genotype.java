package evolution.game;

public record Genotype(
        Action firstMove,
        Action afterCC,
        Action afterCD,
        Action afterDC,
        Action afterDD
) {
    public Genotype {
        if (firstMove == null ||
                afterCC == null ||
                afterCD == null ||
                afterDC == null ||
                afterDD == null)
            throw new IllegalArgumentException("All genes must be defined!");
    }

    public int index() {
        int result = 0;
        for (Action gene : new Action[]{firstMove, afterCC, afterCD, afterDC, afterDD}) {
            result = (result << 1) | (gene == Action.D ? 1 : 0);
        }
        return result;
    }

    public String key() {
        return "" + firstMove + afterCC + afterCD + afterDC + afterDD;
    }

    public static Genotype fromIndex(int index) {
        if (index < 0 || index >= 32) throw new IllegalArgumentException("Genotype index must be 0..31");
        Action[] genes = new Action[5];
        for (int i = 0; i < 5; i++) genes[i] = (index & (1 << (4 - i))) == 0 ? Action.C : Action.D;
        return new Genotype(genes[0], genes[1], genes[2], genes[3], genes[4]);
    }

    public Action actionAfter(Action ownPrevious, Action opponentPrevious) {
        if (ownPrevious == null || opponentPrevious == null) {
            throw new IllegalArgumentException("Previous actions must be defined");
        }
        return switch (ownPrevious) {
            case C -> opponentPrevious == Action.C ? afterCC : afterCD;
            case D -> opponentPrevious == Action.C ? afterDC : afterDD;
        };
    }
}
