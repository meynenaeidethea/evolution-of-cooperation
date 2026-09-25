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
