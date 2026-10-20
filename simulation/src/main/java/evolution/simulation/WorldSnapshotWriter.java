package evolution.simulation;

import evolution.world.CellPosition;
import evolution.world.WorldGrid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Observes the grid without drawing random numbers or changing state. */
public final class WorldSnapshotWriter {
    private WorldSnapshotWriter() {}
    public static void write(Path file, int generation, WorldGrid grid) throws IOException {
        Files.createDirectories(file.getParent());
        int cell = 16, width = grid.sideLength() * cell;
        var svg = new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"" + width
                + "\" height=\"" + (width + 70) + "\" viewBox=\"0 0 " + width + " " + (width + 70) + "\">");
        svg.append("<rect width=\"100%\" height=\"100%\" fill=\"#10232d\"/><g transform=\"translate(0 50)\">");
        for (int r = 0; r < grid.sideLength(); r++) for (int c = 0; c < grid.sideLength(); c++) {
            var position = new CellPosition(r, c);
            if (grid.hasResourceAt(position)) svg.append("<rect x=\"").append(c * cell + 5)
                    .append("\" y=\"").append(r * cell + 5).append("\" width=\"6\" height=\"6\" fill=\"#f7d36b\"/>");
        }
        for (var agent : grid.livingAgents()) {
            int hue = agent.genotype().index() * 360 / 32;
            svg.append("<circle cx=\"").append(agent.position().column() * cell + 8).append("\" cy=\"")
                    .append(agent.position().row() * cell + 8).append("\" r=\"6\" fill=\"hsl(").append(hue)
                    .append(",70%,65%)\"><title>").append(agent.genotype().key()).append(" E=")
                    .append(agent.energyHundredths() / 100.0).append("</title></circle>");
        }
        svg.append("</g><g fill=\"white\" font-family=\"sans-serif\"><text x=\"12\" y=\"22\">Generation ")
                .append(generation).append(" · agents ").append(grid.livingAgents().size())
                .append("</text><text x=\"12\" y=\"42\" font-size=\"12\">Color: genotype · yellow square: resource · hover: energy</text></g></svg>");
        Files.writeString(file, svg, StandardCharsets.UTF_8);
    }
}
