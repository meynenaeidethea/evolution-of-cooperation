package evolution.ui;

import evolution.simulation.*;
import java.nio.file.Path;
import evolution.simulation.SimulationHistory;
import evolution.analysis.MetricTimeline;
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
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
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
    private SimulationConfig config = SimulationConfig.baseline();
    private PairingMode activePairing = PairingMode.LOCAL;
    private String activeProfile = "user";
    private final ProfileSelector profile = new ProfileSelector();
    private final ComboBox<PairingMode> pairing = new ComboBox<>();
    private final Label parameters = new Label();
    private final SimulationHistory history = new SimulationHistory(config, 1);
    private final WorldCanvas world = new WorldCanvas();
    private final MetricCharts charts = new MetricCharts(tick -> navigate(() -> history.seek(history.nearestIndex(tick))));
    private final Tab chartsTab = new Tab("Графики", charts);
    private long lastChartUpdate;
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
        pairing.setConverter(new StringConverter<>() {
            public String toString(PairingMode p) { return p==null?"":switch(p) {case LOCAL -> "Локальные";case GLOBAL -> "Глобальные";case GLOBAL_MATCHED -> "Глобальные с бюджетом";}; }
            public PairingMode fromString(String p) { return PairingMode.valueOf(p); }
        });
        pairing.getItems().setAll(PairingMode.values());pairing.setValue(PairingMode.LOCAL);
        parameters.setWrapText(true);
        String startupProfile=getParameters().getNamed().getOrDefault("profile","profiles/user.properties");
        try {
            profile.select(Path.of(startupProfile));
            config = ConfigProfiles.load(profile.selectedPath());
            history.newRun(config, 1);
            activeProfile = profile.selectedPath().toString();
        } catch (Exception e) { seedError.setText("Ошибка профиля: " + e.getMessage()); }
        Button export=new Button("Экспорт кадра PNG + SVG");
        export.setOnAction(event -> {
            pause();
            var chooser=new javafx.stage.FileChooser();chooser.setInitialFileName("world-seed-"+history.currentSnapshot().seed()+"-tick-"+history.currentSnapshot().elapsedTicks()+".png");
            var file=chooser.showSaveDialog(stage);
            if(file!=null) try { SnapshotExporter.write(file.toPath(),history.currentSnapshot(),"UI "+activePairing); seedError.setText("Кадр сохранён: "+file); }
            catch(Exception e) {seedError.setText("Ошибка экспорта: "+e.getMessage());}
            refresh();
        });
        timeline.setCycleCount(Animation.INDEFINITE);
        seed.setPrefColumnCount(18);
        seed.setTooltip(new Tooltip("Seed типа long; тот же seed воспроизводит тот же запуск"));
        seed.setOnAction(event -> newRun());
        Button newRun = new Button("Новый запуск");
        newRun.setOnAction(event -> newRun());
        seedError.setStyle("-fx-text-fill: #b42318;");
        heading.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        VBox top = new VBox(8, heading, new HBox(10, new Label("Seed:"), seed, newRun), new FlowPane(10, 8, profile, profile.openButton(stage, this::newRun, seedError::setText), pairing, export), parameters, seedError);
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
        speed.getItems().setAll(1, 2, 5, 10, 20, 50);
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
        speed.setTooltip(new Tooltip("Скорость автовоспроизведения: 1× = 5 тактов/с, 20× = 100 тактов/с, 50× = 250 тактов/с"));
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
        TabPane views = new TabPane(new Tab("Мир", map), chartsTab);
        views.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        views.getSelectionModel().selectedItemProperty().addListener((observable, before, after) -> {
            if (after == chartsTab) refreshCharts(true);
        });
        root.setCenter(views);
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
        if(getParameters().getNamed().containsKey("smoke-dir")) javafx.application.Platform.runLater(() -> smoke(stage,views));
    }

    /** Opt-in integration check runs the real JavaFX scene and exports reviewable evidence. */
    private void smoke(Stage stage,TabPane views) {
        Path out=Path.of(getParameters().getNamed().get("smoke-dir"));
        try {
            java.nio.file.Files.createDirectory(out);
            Path custom = out.resolve("user.properties");
            java.nio.file.Files.copy(Path.of("profiles/demo.properties"), custom);
            profile.select(custom);
            seed.setText("17"); newRun();
            if (!config.equals(ConfigProfiles.load(custom))) throw new IllegalStateException("Custom profile lost");
            seed.setText("18"); newRun();
            if (!profile.selectedPath().equals(custom.toAbsolutePath().normalize())
                    || !config.equals(ConfigProfiles.load(custom))) throw new IllegalStateException("Custom restart lost");
            Path invalid = out.resolve("invalid.properties");
            java.nio.file.Files.writeString(invalid, "sideLength=20\n");
            try { profile.select(invalid); throw new IllegalStateException("Invalid profile accepted"); }
            catch (IllegalArgumentException expected) { /* Previous valid selection survives. */ }
            if (!profile.selectedPath().equals(custom.toAbsolutePath().normalize())) throw new IllegalStateException("Invalid selection replaced profile");
            seed.setText("invalid");newRun();
            if(seedError.getText().isEmpty()) throw new IllegalStateException("Missing invalid-seed error");
            profile.select(Path.of("profiles/research.properties"));pairing.setValue(PairingMode.GLOBAL_MATCHED);seed.setText("10001");newRun();
            long begin=System.nanoTime();
            for(int i=0;i<320;i++) history.stepForward();
            if(history.size()!=300 || history.toStart().elapsedTicks()!=21) throw new IllegalStateException("History window");
            history.toEnd();refresh();
            SnapshotExporter.write(out.resolve("selected-frame.png"),history.currentSnapshot(),"UI global-matched");
            stage.getScene().getRoot().applyCss();stage.getScene().getRoot().layout();
            saveScene(stage,out.resolve("world-ui.png"));
            views.getSelectionModel().select(chartsTab);refreshCharts(true);
            history.seek(history.nearestIndex(300));refresh();
            stage.getScene().getRoot().applyCss();stage.getScene().getRoot().layout();
            saveScene(stage,out.resolve("charts-ui.png"));
            speed.setValue(50);play.fire();
            var wait=new javafx.animation.PauseTransition(Duration.millis(250));
            wait.setOnFinished(event -> {
                try {
                    play.fire();
                    if(isPlaying() || history.currentSnapshot().elapsedTicks()<=300) throw new IllegalStateException("Playback/pause");
                    java.nio.file.Files.writeString(out.resolve("success.txt"),"JavaFX scene, profile, seed validation, 320 ticks, history cap, replay, charts, frame export, speed/play/pause: PASS\nseconds="+(System.nanoTime()-begin)/1e9+"\n");
                    stage.close();javafx.application.Platform.exit();
                } catch(Exception e) { e.printStackTrace();System.exit(1); }
            });wait.play();
        } catch(Exception e) { e.printStackTrace();System.exit(1); }
    }
    private void saveScene(Stage stage,Path file) throws java.io.IOException {
        var fx=stage.getScene().getRoot().snapshot(null,null);
        var img=new java.awt.image.BufferedImage((int)fx.getWidth(),(int)fx.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<img.getHeight();y++) for(int x=0;x<img.getWidth();x++) img.setRGB(x,y,fx.getPixelReader().getArgb(x,y));
        javax.imageio.ImageIO.write(img,"png",file.toFile());
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
        try {
            var next=ConfigProfiles.load(profile.selectedPath());
            history.newRun(next, value, pairing.getValue());
            config=next;activePairing=pairing.getValue();activeProfile=profile.selectedPath().toString();
            seedError.setText("");
        } catch(Exception e) { seedError.setText("Ошибка запуска: "+e.getMessage()); }
        refresh();
    }

    private void navigate(Runnable action) {
        pause();
        action.run();
        refresh();
    }

    private void advance() {
        try { history.stepForward(); }
        catch(RuntimeException e) { pause(); seedError.setText("Ошибка расчёта: "+e.getMessage()); return; }
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
        var generation = values.generationMetrics();
        var games = generation.gameStatistics();
        world.show(snapshot);
        refreshCharts(!isPlaying());
        parameters.setText(String.format(Locale.ROOT,
                "Активный профиль: %s | %s | Мир %d×%d, агентов %d, тактов/поколение %d, поколений %d, раундов %d\n"
                + "Энергия: начальная %.2f, ресурс %.2f, стоимость такта %.2f, вес игры %.2f | q=%.3f, скрещивание=%.2f, мутация=%.3f, турнир=%d | %s",
                activeProfile,pairing.getConverter().toString(activePairing),config.sideLength(),config.sideLength(),config.populationSize(),
                config.ticksPerGeneration(),config.maximumGenerations(),config.roundsPerMatch(),config.initialEnergyHundredths()/100.,
                config.resourceEnergyHundredths()/100.,config.livingCostHundredths()/100.,config.gamePayoffWeightHundredths()/100.,
                config.resourceSpawnProbability(),config.crossoverProbability(),config.mutationProbability(),config.tournamentSize(),SimulationSession.MODEL_VERSION));
        heading.setText("Поколение " + snapshot.generation() + " / " + config.maximumGenerations()
                + "   ·   Такт " + snapshot.tick() + " / " + config.ticksPerGeneration());
        String status = switch (snapshot.status()) {
            case "completed" -> "Завершён";
            case "extinct" -> "Популяция вымерла";
            default -> "В процессе";
        };
        metrics.setText("Живых агентов: " + values.livingAgents()
                + "\nВыживаемость: " + percentage(generation.survivalFraction())
                + "\nСредняя энергия: " + energy(values.averageEnergyHundredths())
                + "\nМаксимальная энергия: " + energy(generation.maximumEnergyHundredths())
                + "\nРесурсов: " + values.resourceCount()
                + "\nЭнергия от ресурсов: " + energy(values.resourceEnergyHundredths())
                + "\nИгровой выигрыш: " + values.gamePayoff()
                + "\nЭнергия от игры: " + energy(generation.gameEnergyHundredths())
                + "\nC в первых раундах: " + percentage(games.firstRoundCooperationRate())
                + "\nC во всех раундах: " + percentage(games.cooperationRate())
                + "\nКластеризация: " + percentage(generation.spatialClustering())
                + "\nSeed: " + snapshot.seed()
                + "\nЗапуск: " + status
                + "\nПросмотр: " + (isPlaying() ? "Автовоспроизведение" : "Пауза"));
        savedTick.setText("Сохранённый такт: " + snapshot.elapsedTicks()
                + "   ·   Снимков: " + history.size() + "/" + history.capacity()
                + " (окно истории; более ранние удаляются). Первый доступный такт: " + history.snapshots().getFirst().elapsedTicks());
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

    private void refreshCharts(boolean force) {
        if (!chartsTab.isSelected()) return;
        long now = System.nanoTime();
        if (force || now - lastChartUpdate >= 200_000_000L) {
            charts.show(MetricTimeline.fromSnapshots(history.snapshots(), history.cursor()));
            lastChartUpdate = now;
        }
    }

    private String percentage(double fraction) {
        return Double.isNaN(fraction) ? "—" : String.format(Locale.ROOT, "%.1f%%", fraction * 100);
    }

    @Override
    public void stop() {
        timeline.stop();
    }
}
