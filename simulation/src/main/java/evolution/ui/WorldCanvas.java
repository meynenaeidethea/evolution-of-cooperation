package evolution.ui;

import evolution.game.Action;
import evolution.game.Genotype;
import evolution.simulation.SimulationSnapshot;
import javafx.scene.canvas.Canvas;
import javafx.scene.paint.Color;

/** Rendering reads only immutable snapshots; no mutable world enters the UI. */
final class WorldCanvas extends Canvas {
    private SimulationSnapshot snapshot;

    WorldCanvas() {
        super(600, 600);
    }

    void show(SimulationSnapshot snapshot) {
        this.snapshot = snapshot;
        draw();
    }

    void resizeSquare(double size) {
        setWidth(size);
        setHeight(size);
        draw();
    }

    static String genotypeKey(Genotype genotype) {
        return "" + genotype.firstMove() + genotype.afterCC() + genotype.afterCD()
                + genotype.afterDC() + genotype.afterDD();
    }

    static Color genotypeColor(Genotype genotype) {
        return genotypeColor(genotypeKey(genotype));
    }

    static Color genotypeColor(String key) {
        int index = 0;
        for (int i = 0; i < key.length(); i++) {
            index = index * 2 + (key.charAt(i) == Action.D.name().charAt(0) ? 1 : 0);
        }
        return Color.hsb(index * 137.508 % 360, 0.70, index % 2 == 0 ? 0.85 : 0.65);
    }

    private void draw() {
        if (snapshot == null) {
            return;
        }
        var graphics = getGraphicsContext2D();
        double cell = getWidth() / snapshot.sideLength();
        graphics.setFill(Color.web("#f7fafc"));
        graphics.fillRect(0, 0, getWidth(), getHeight());
        graphics.setStroke(Color.web("#d5dfe8"));
        graphics.setLineWidth(0.7);
        for (int i = 0; i <= snapshot.sideLength(); i++) {
            double coordinate = Math.min(getWidth() - 0.5, i * cell + 0.5);
            graphics.strokeLine(coordinate, 0, coordinate, getHeight());
            graphics.strokeLine(0, coordinate, getWidth(), coordinate);
        }
        for (var agent : snapshot.agents()) {
            graphics.setFill(genotypeColor(agent.genotype()));
            graphics.fillOval((agent.position().column() + 0.19) * cell,
                    (agent.position().row() + 0.19) * cell, cell * 0.62, cell * 0.62);
        }
        // Center markers are drawn last and remain visible even on an occupied cell.
        for (var resource : snapshot.resources()) {
            double x = (resource.column() + 0.395) * cell;
            double y = (resource.row() + 0.395) * cell;
            graphics.setFill(Color.web("#efb72c"));
            graphics.fillRect(x, y, cell * 0.21, cell * 0.21);
            graphics.setStroke(Color.web("#805a00"));
            graphics.strokeRect(x, y, cell * 0.21, cell * 0.21);
        }
    }
}
