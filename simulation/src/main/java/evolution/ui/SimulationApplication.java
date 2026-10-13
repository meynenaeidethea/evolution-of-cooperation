package evolution.ui;

import evolution.simulation.SimulationConfig;
import evolution.simulation.SimulationHistory;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.stage.Stage;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.util.Locale;

public final class SimulationApplication extends Application {
    private final SimulationConfig config = SimulationConfig.baseline();
    private final SimulationHistory history = new SimulationHistory(config, 1);
    private final WorldCanvas world = new WorldCanvas();
    private final TextField seed = new TextField("1");
    private final Label seedError = new Label();
    private final Label heading = new Label();
    private final Label metrics = new Label();
    private final Label savedTick = new Label();
    private final Slider slider = new Slider(0, 1, 0);
    private final Button back = new Button("Назад");
    private final Button start = new Button("В начало");
    private final Button end = new Button("В конец");
    private final Button forward = new Button("Шаг вперёд");
    private final Button play = new Button("Автовоспроизведение");
    private final ComboBox<Integer> speed = new ComboBox<>();
    private final Timeline timeline = new Timeline(new KeyFrame(Duration.millis(200), event -> advance()));
    private boolean refreshing;

    @Override
    public void start(Stage stage) {
        timeline.setCycleCount(Animation.INDEFINITE);
        seed.setPrefColumnCount(18);
        seed.setTooltip(new Tooltip("Seed типа long; тот же seed воспроизводит тот же запуск"));
        seed.setOnAction(event -> newRun());
        Button newRun = new Button("Новый запуск");
        newRun.setOnAction(event -> newRun());
        seedError.setStyle("-fx-text-fill: #b42318;");
        heading.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        VBox top = new VBox(8, heading, new HBox(10, new Label("Seed:"), seed, newRun), seedError);
        top.setPadding(new Insets(0, 0, 12, 0));

        StackPane map = new StackPane(world);
        map.setMinSize(240, 240);
        map.widthProperty().addListener((observable, before, after) -> resizeWorld(map));
        map.heightProperty().addListener((observable, before, after) -> resizeWorld(map));

        metrics.setStyle("-fx-font-size: 14px; -fx-line-spacing: 4px;");
        VBox sidebar = new VBox(16, metrics, legend());
        sidebar.setPadding(new Insets(8));
        ScrollPane scroll = new ScrollPane(sidebar);
        scroll.setFitToWidth(true);
        scroll.setPrefWidth(285);
        scroll.setMinWidth(270);

        back.setOnAction(event -> navigate(history::back));
        start.setOnAction(event -> navigate(history::toStart));
        end.setOnAction(event -> navigate(history::toEnd));
        end.setTooltip(new Tooltip("Последний сохранённый такт; будущие такты не вычисляются"));
        forward.setOnAction(event -> {
            pause();
            advance();
        });
        play.setOnAction(event -> {
            if (isPlaying()) {
                pause();
            } else if (history.canStepForward()) {
                timeline.play();
            }
            refresh();
        });
        slider.setBlockIncrement(1);
        slider.setMajorTickUnit(1);
        slider.setMinorTickCount(0);
        slider.setSnapToTicks(true);
        slider.setTooltip(new Tooltip("Выбрать сохранённый такт запуска"));
        slider.valueProperty().addListener((observable, before, after) -> {
            if (!refreshing) {
                pause();
                history.seek((int) Math.round(after.doubleValue()));
                refresh();
            }
        });
        speed.getItems().setAll(1, 2, 5, 10, 20);
        speed.setConverter(new StringConverter<>() {
            @Override
            public String toString(Integer value) {
                return value == null ? "" : value + "×";
            }

            @Override
            public Integer fromString(String value) {
                return Integer.valueOf(value.replace("×", ""));
            }
        });
        speed.setValue(1);
        speed.setTooltip(new Tooltip("Скорость автовоспроизведения: 1× = 5 тактов/с, 20× = 100 тактов/с"));
        speed.valueProperty().addListener((observable, before, after) -> {
            if (after != null) {
                timeline.setRate(after);
            }
        });
        FlowPane buttons = new FlowPane(8, 8, start, back, forward, end, play,
                new HBox(6, new Label("Скорость:"), speed));
        VBox bottom = new VBox(8, buttons, savedTick, slider);
        bottom.setPadding(new Insets(12, 0, 0, 0));

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(16));
        root.setTop(top);
        root.setCenter(map);
        root.setRight(scroll);
        root.setBottom(bottom);
        BorderPane.setMargin(scroll, new Insets(0, 0, 0, 16));
        root.setStyle("-fx-font-family: sans-serif; -fx-background-color: #eef2f6;");
        stage.setTitle("Эволюция сотрудничества");
        stage.setScene(new Scene(root, 1040, 860));
        stage.setMinWidth(840);
        stage.setMinHeight(600);
        refresh();
        stage.show();
    }

    private VBox legend() {
        VBox legend = new VBox(5, new Label("Генотипы"));
        for (int row = 0; row < 16; row++) {
            HBox entries = new HBox(16);
            for (int column = 0; column < 2; column++) {
                int index = row * 2 + column;
                String key = String.format(Locale.ROOT, "%5s", Integer.toBinaryString(index))
                        .replace(' ', '0').replace('0', 'C').replace('1', 'D');
                entries.getChildren().add(new HBox(5,
                        new Circle(5, WorldCanvas.genotypeColor(key)), new Label(key)));
            }
            legend.getChildren().add(entries);
        }
        return legend;
    }

    private void resizeWorld(StackPane container) {
        world.resizeSquare(Math.max(1, Math.min(container.getWidth(), container.getHeight())));
    }

    private void newRun() {
        final long value;
        try {
            value = Long.parseLong(seed.getText().strip());
        } catch (NumberFormatException exception) {
            seedError.setText("Введите целое число от −9223372036854775808 до 9223372036854775807.");
            return;
        }
        pause();
        history.newRun(config, value);
        seedError.setText("");
        refresh();
    }

    private void navigate(Runnable action) {
        pause();
        action.run();
        refresh();
    }

    private void advance() {
        history.stepForward();
        if (!history.canStepForward()) {
            pause();
        }
        refresh();
    }

    private boolean isPlaying() {
        return timeline.getStatus() == Animation.Status.RUNNING;
    }

    private void pause() {
        timeline.pause();
    }

    private void refresh() {
        var snapshot = history.currentSnapshot();
        var values = snapshot.metrics();
        world.show(snapshot);
        heading.setText("Поколение " + snapshot.generation() + " / " + config.maximumGenerations()
                + "   ·   Такт " + snapshot.tick() + " / " + config.ticksPerGeneration());
        String status = switch (snapshot.status()) {
            case "completed" -> "Завершён";
            case "extinct" -> "Популяция вымерла";
            default -> "В процессе";
        };
        metrics.setText("Живых агентов: " + values.livingAgents()
                + "\nСредняя энергия: " + energy(values.averageEnergyHundredths())
                + "\nРесурсов: " + values.resourceCount()
                + "\nЭнергия от ресурсов: " + energy(values.resourceEnergyHundredths())
                + "\nИгровой выигрыш: " + values.gamePayoff()
                + "\nSeed: " + snapshot.seed()
                + "\nЗапуск: " + status
                + "\nПросмотр: " + (isPlaying() ? "Автовоспроизведение" : "Пауза"));
        savedTick.setText("Сохранённый такт: " + snapshot.elapsedTicks()
                + " / " + (history.size() - 1) + "   ·   Снимков: " + history.size());
        refreshing = true;
        try {
            slider.setMax(Math.max(1, history.size() - 1));
            slider.setValue(history.cursor());
            slider.setDisable(history.size() == 1);
        } finally {
            refreshing = false;
        }
        back.setDisable(history.cursor() == 0);
        start.setDisable(history.cursor() == 0);
        end.setDisable(history.cursor() == history.size() - 1);
        forward.setDisable(!history.canStepForward());
        play.setDisable(!history.canStepForward());
        play.setText(isPlaying() ? "Пауза" : "Автовоспроизведение");
    }

    private String energy(double hundredths) {
        return Double.isNaN(hundredths) ? "—" : String.format(Locale.ROOT, "%.2f", hundredths / 100);
    }

    @Override
    public void stop() {
        timeline.stop();
    }
}
