package evolution.ui;

import evolution.analysis.MetricTimeline;
import javafx.geometry.Insets;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;

import java.util.List;
import java.util.Locale;
import java.util.function.LongConsumer;

/** UI-only rendering of detached timeline data. Missing values and generation resets break lines. */
final class MetricCharts extends GridPane {
    private final Plot cooperation;
    private final Plot survival;
    private final Plot energy;
    private final Plot genotypes;

    MetricCharts(LongConsumer selectTick) {
        cooperation = new Plot("Сотрудничество, %", true, selectTick);
        survival = new Plot("Выживаемость, %", true, selectTick);
        energy = new Plot("Средняя энергия", false, selectTick);
        genotypes = new Plot("Частоты ведущих генотипов, %", true, selectTick);
        setPadding(new Insets(12));
        setHgap(12);
        setVgap(12);
        for (int i = 0; i < 2; i++) {
            var column = new ColumnConstraints();
            column.setPercentWidth(50);
            getColumnConstraints().add(column);
            var row = new RowConstraints();
            row.setPercentHeight(50);
            getRowConstraints().add(row);
        }
        add(cooperation, 0, 0);
        add(survival, 1, 0);
        add(energy, 0, 1);
        add(genotypes, 1, 1);
    }

    void show(MetricTimeline data) {
        cooperation.show(data, data.cooperation(), List.of(Color.web("#2563eb"), Color.web("#d97706")));
        survival.show(data, List.of(data.survival()), List.of(Color.web("#16a34a")));
        energy.show(data, List.of(data.energy()), List.of(Color.web("#9333ea")));
        genotypes.show(data, data.genotypes(), data.genotypes().stream()
                .map(series -> WorldCanvas.genotypeColor(series.name())).toList());
    }

    private static final class Plot extends VBox {
        private static final double LEFT = 43;
        private static final double RIGHT = 14;
        private static final double TOP = 18;
        private static final double BOTTOM = 36;
        private final Canvas canvas = new Canvas(260, 200);
        private final FlowPane legend = new FlowPane(10, 3);
        private final boolean percent;
        private MetricTimeline data;
        private List<MetricTimeline.Series> series = List.of();
        private List<Color> colors = List.of();

        Plot(String title, boolean percent, LongConsumer selectTick) {
            this.percent = percent;
            Label heading = new Label(title);
            heading.setStyle("-fx-font-weight: bold;");
            heading.setWrapText(true);
            setSpacing(6);
            setPadding(new Insets(10));
            setMinSize(0, 0);
            setStyle("-fx-background-color: white; -fx-background-radius: 6;");
            canvas.setManaged(false);
            StackPane area = new StackPane(canvas);
            area.setMinSize(0, 70);
            VBox.setVgrow(area, Priority.ALWAYS);
            area.widthProperty().addListener((observable, before, after) -> {
                canvas.setWidth(Math.max(80, after.doubleValue()));
                draw();
            });
            area.heightProperty().addListener((observable, before, after) -> {
                canvas.setHeight(Math.max(70, after.doubleValue()));
                draw();
            });
            getChildren().addAll(heading, legend, area);
            Tooltip.install(canvas, new Tooltip("Нажмите на график, чтобы выбрать такт. Пунктир — начало поколения."
                    + (title.startsWith("Частоты") ? " Генотипы выбраны по максимальной численности за показанный период." : "")));
            canvas.setOnMouseClicked(event -> {
                if (data == null || event.getX() < LEFT || event.getX() > canvas.getWidth() - RIGHT) return;
                double fraction = (event.getX() - LEFT) / (canvas.getWidth() - LEFT - RIGHT);
                selectTick.accept(Math.round(fraction * data.selectedTick()));
            });
        }

        void show(MetricTimeline data, List<MetricTimeline.Series> series, List<Color> colors) {
            this.data = data;
            if (!this.series.stream().map(MetricTimeline.Series::name).toList()
                    .equals(series.stream().map(MetricTimeline.Series::name).toList())) {
                legend.getChildren().clear();
                for (int i = 0; i < series.size(); i++) {
                    var entry = new javafx.scene.layout.HBox(4, new Circle(4, colors.get(i)),
                            new Label(series.get(i).name()));
                    entry.setStyle("-fx-font-size: 11px;");
                    legend.getChildren().add(entry);
                }
            }
            this.series = series;
            this.colors = colors;
            draw();
        }

        private void draw() {
            var graphics = canvas.getGraphicsContext2D();
            double width = canvas.getWidth();
            double height = canvas.getHeight();
            graphics.clearRect(0, 0, width, height);
            if (data == null) return;
            double plotWidth = width - LEFT - RIGHT;
            double plotHeight = height - TOP - BOTTOM;
            double maximum = percent ? 100 : Math.max(1, series.stream().flatMap(s -> s.points().stream())
                    .mapToDouble(MetricTimeline.Point::value).filter(Double::isFinite).max().orElse(1) * 1.1);
            long lastTick = Math.max(1, data.selectedTick());
            graphics.setFont(Font.font(10));
            graphics.setLineWidth(0.7);
            graphics.setLineDashes();
            for (int i = 0; i <= 4; i++) {
                double y = TOP + plotHeight * i / 4;
                graphics.setStroke(Color.web("#e2e8f0"));
                graphics.strokeLine(LEFT, y, width - RIGHT, y);
                graphics.setFill(Color.web("#64748b"));
                graphics.fillText(String.format(Locale.ROOT, "%.0f", maximum * (4 - i) / 4), 2, y + 3);
            }
            long previousLabel = -1;
            for (int i = 0; i <= 2; i++) {
                long tick = Math.round(data.selectedTick() * i / 2.0);
                if (tick == previousLabel) continue;
                previousLabel = tick;
                double x = LEFT + plotWidth * tick / lastTick;
                graphics.fillText(Long.toString(tick), x - 6,
                        height - BOTTOM + 15);
            }
            graphics.fillText("Такт запуска", LEFT + plotWidth / 2 - 30, height - 3);
            graphics.setStroke(Color.web("#94a3b8"));
            graphics.setLineDashes(3, 4);
            for (long tick : data.generationStarts()) {
                double x = LEFT + plotWidth * tick / lastTick;
                graphics.strokeLine(x, TOP, x, height - BOTTOM);
            }
            graphics.setLineDashes();
            boolean hasValues = false;
            for (int index = 0; index < series.size(); index++) {
                graphics.setStroke(colors.get(index));
                graphics.setFill(colors.get(index));
                graphics.setLineWidth(1.6);
                MetricTimeline.Point previous = null;
                for (var point : series.get(index).points()) {
                    if (!Double.isFinite(point.value())) {
                        previous = null;
                        continue;
                    }
                    hasValues = true;
                    double x = LEFT + plotWidth * point.tick() / lastTick;
                    double y = TOP + plotHeight * (1 - point.value() / maximum);
                    if (previous != null && previous.generation() == point.generation()) {
                        graphics.strokeLine(LEFT + plotWidth * previous.tick() / lastTick,
                                TOP + plotHeight * (1 - previous.value() / maximum), x, y);
                    }
                    if (previous == null || previous.generation() != point.generation()
                            || point.tick() == data.selectedTick()) {
                        graphics.fillOval(x - 2, y - 2, 4, 4);
                    }
                    previous = point;
                }
            }
            if (!hasValues) {
                graphics.setFill(Color.web("#64748b"));
                graphics.fillText("Нет наблюдений", LEFT + 8, TOP + plotHeight / 2);
            }
        }
    }
}
