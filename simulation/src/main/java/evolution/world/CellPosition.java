package evolution.world;

public record CellPosition(int row, int column) {

    public CellPosition moveBy(int rowDelta, int columnDelta, int sideLength) {
        if (sideLength < 1) {
            throw new IllegalArgumentException("Side length must be at least 1");
        }

        return new CellPosition(
                Math.floorMod(row + rowDelta, sideLength),
                Math.floorMod(column + columnDelta, sideLength)
        );
    }
}
