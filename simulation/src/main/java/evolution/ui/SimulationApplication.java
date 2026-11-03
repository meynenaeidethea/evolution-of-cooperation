package evolution.ui;

import evolution.analysis.MetricTimeline;
import evolution.replay.RecordedHistory;
import evolution.simulation.*;
import javafx.animation.*;
import javafx.application.*;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.nio.file.*;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/** JavaFX owns controls and detached views; only the worker touches sessions, journals and files. */
public final class SimulationApplication extends Application {
    private final SimulationWorker worker=new SimulationWorker(Platform::runLater);
    private RecordedHistory history; // Accessed only on simulation-worker.
    private RecordedHistory.View view; // Accessed only on JavaFX.
    private SimulationConfig editedConfig;
    private final ProfileSelector profile=new ProfileSelector();
    private final ComboBox<PairingMode> pairing=new ComboBox<>();
    private final TextField seed=new TextField("10001");
    private final Label heading=new Label(), parameters=new Label(), message=new Label(), metrics=new Label(), savedTick=new Label();
    private final WorldCanvas world=new WorldCanvas();
    private final MetricCharts charts=new MetricCharts(tick->navigate(()->history.seek(Math.toIntExact(tick))));
    private final Tab chartsTab=new Tab("Графики",charts);
    private final Slider slider=new Slider(0,1,0);
    private final ComboBox<Integer> speed=new ComboBox<>();
    private final Button back=new Button("Назад"),start=new Button("В начало"),end=new Button("К последнему кадру"),
            forward=new Button("Шаг вперёд"),play=new Button("Воспроизвести"),newRun=new Button("Новый запуск"),
            demo=new Button("Демонстрация"),edit=new Button("Параметры…"),openProfile=new Button("Открыть профиль…"),
            saveProfile=new Button("Сохранить профиль…"),openReplay=new Button("Открыть запись…"),
            saveReplay=new Button("Сохранить копию записи…"),export=new Button("Кадр PNG + SVG");
    private final Timeline timer=new Timeline(new KeyFrame(Duration.millis(200),event->advance()));
    private boolean playing,refreshing;
    private Stage stage;

    @Override public void start(Stage stage) {
        this.stage=stage;
        pairing.getItems().setAll(PairingMode.values());pairing.setValue(PairingMode.LOCAL);
        pairing.setConverter(new StringConverter<>() {
            @Override public String toString(PairingMode p) {return p==null?"":switch(p) {
                case LOCAL->"Локальные встречи";case GLOBAL->"Глобальные встречи";case GLOBAL_MATCHED->"Глобальные, число пар как локально";};}
            @Override public PairingMode fromString(String s) {return PairingMode.valueOf(s);}
        });
        seed.setPrefColumnCount(14);seed.setOnAction(e->newRun());
        seed.setTooltip(new Tooltip("Целочисленный seed. Те же параметры и seed воспроизводят запуск."));
        profile.onChange(()->editedConfig=null);
        newRun.setOnAction(e->newRun());
        demo.setOnAction(e->{profile.choose(Path.of("profiles/demo.properties"));editedConfig=null;seed.setText("10001");pairing.setValue(PairingMode.LOCAL);newRun();});
        openProfile.setOnAction(e->{
            var file=chooser("Открыть профиль","Профиль (*.properties)","*.properties").showOpenDialog(stage);
            if(file==null) return;
            pause();
            submit(()->ConfigProfiles.load(file.toPath()),c->{
                profile.choose(file.toPath());editedConfig=c;newRun();
            });
        });
        edit.setOnAction(e->{
            pause();
            var selected=profile.selectedPath();var changed=editedConfig;
            submit(()->changed!=null?changed:ConfigProfiles.load(selected),c->
                new ParameterEditor(stage,c).showAndWait().ifPresent(next->{editedConfig=next;newRun();}));
        });
        saveProfile.setOnAction(e->{
            pause();var current=view.metadata().config();
            var chooser=chooser("Сохранить параметры активного запуска","Профиль (*.properties)","*.properties");
            chooser.setInitialFileName("custom.properties");var file=chooser.showSaveDialog(stage);
            if(file!=null) submit(()->{Files.writeString(file.toPath(),ConfigProfiles.format(current),StandardOpenOption.CREATE_NEW);return file.toPath();},
                    path->message.setText("Профиль сохранён: "+path));
        });
        openReplay.setOnAction(e->{
            pause();var file=chooser("Открыть запись мира","Запись (*.eoc)","*.eoc").showOpenDialog(stage);
            if(file!=null) submit(()->replace(RecordedHistory.open(file.toPath())),next->{
                editedConfig=next.metadata().config();seed.setText(""+next.metadata().seed());pairing.setValue(next.metadata().pairing());apply(next);
            });
        });
        saveReplay.setOnAction(e->{
            pause();var chooser=chooser("Сохранить копию всех рассчитанных кадров","Запись (*.eoc)","*.eoc");
            chooser.setInitialFileName("world-seed-"+view.metadata().seed()+".eoc");var file=chooser.showSaveDialog(stage);
            if(file!=null) submit(()->{history.copyTo(file.toPath());return file.toPath();},p->message.setText("Запись сохранена: "+p));
        });
        export.setOnAction(e->{
            pause();var snapshot=view.snapshot();var mode=view.metadata().pairing();
            var chooser=chooser("Экспорт выбранного кадра","Изображение (*.png)","*.png");
            chooser.setInitialFileName("world-seed-"+snapshot.seed()+"-tick-"+snapshot.elapsedTicks()+".png");
            var file=chooser.showSaveDialog(stage);
            if(file!=null) submit(()->{SnapshotExporter.write(file.toPath(),snapshot,"UI "+mode);return file.toPath();},
                    p->message.setText("Кадр сохранён: "+p));
        });
        back.setOnAction(e->navigate(()->history.back()));start.setOnAction(e->navigate(()->history.toStart()));
        end.setOnAction(e->navigate(()->history.toEnd()));forward.setOnAction(e->{pause();advance();});
        end.setTooltip(new Tooltip("Перейти к последнему рассчитанному кадру. Будущее не вычисляется."));
        play.setOnAction(e->{if(playing) pause();else if(view!=null && view.canStepForward()) {playing=true;timer.play();}controls();});
        speed.getItems().setAll(1,2,5,10,20,50);speed.setValue(1);
        speed.setConverter(new StringConverter<>() {
            public String toString(Integer n) {return n+"×";}public Integer fromString(String s) {return Integer.valueOf(s.replace("×",""));}
        });
        speed.setTooltip(new Tooltip("Целевая скорость: 1× = 5 тактов/с. На тяжёлом мире фактическая скорость ниже."));
        speed.valueProperty().addListener((o,a,b)->timer.setRate(b));timer.setCycleCount(Animation.INDEFINITE);
        slider.setBlockIncrement(1);slider.setSnapToTicks(true);
        slider.setTooltip(new Tooltip("Все рассчитанные кадры, включая кадры прежних поколений."));
        slider.valueProperty().addListener((o,a,b)->{
            if(!refreshing && !worker.busy()) {int target=(int)Math.round(b.doubleValue());navigate(()->history.seek(target));}
        });
        heading.setStyle("-fx-font-size: 19px; -fx-font-weight: bold;");parameters.setWrapText(true);
        parameters.setMaxWidth(Double.MAX_VALUE);message.setWrapText(true);message.setStyle("-fx-text-fill: #9a3412;");
        var intro=new Label("Агенты собирают ресурсы, играют и передают стратегии потомкам. Начните с демонстрации; изменяйте правила в «Параметрах»." );intro.setWrapText(true);
        var top=new VBox(7,heading,intro,new FlowPane(8,8,demo,profile,new Label("Seed:"),seed,pairing,newRun),
                new FlowPane(8,8,edit,openProfile,saveProfile,openReplay,saveReplay,export),parameters,message);
        top.setPadding(new Insets(0,0,10,0));
        var map=new StackPane(world);map.setMinSize(200,200);
        map.widthProperty().addListener((o,a,b)->resize(map));map.heightProperty().addListener((o,a,b)->resize(map));
        var views=new TabPane(new Tab("Мир",map),chartsTab);views.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        views.getSelectionModel().selectedItemProperty().addListener((o,a,b)->{if(b==chartsTab) refreshCharts();});
        metrics.setStyle("-fx-font-size: 13px; -fx-line-spacing: 3px;");
        var legend=new Label("Цвет — генотип (пять правил C/D).\nКруг — агент; золотой квадрат — ресурс.\nC — сотрудничать, D — предать.\nПамять действует внутри одной встречи.\n\nКластеризация — доля соседних пар\nс одинаковым генотипом.");legend.setWrapText(true);
        var sidebar=new VBox(16,metrics,legend,genotypeLegend());sidebar.setPadding(new Insets(10));
        var scroll=new ScrollPane(sidebar);scroll.setFitToWidth(true);scroll.setPrefWidth(310);scroll.setMinWidth(280);
        savedTick.setWrapText(true);
        var bottom=new VBox(7,new FlowPane(8,8,start,back,forward,end,play,new Label("Скорость:"),speed),savedTick,slider);
        bottom.setPadding(new Insets(10,0,0,0));
        var root=new BorderPane();root.setPadding(new Insets(16));root.setTop(top);root.setCenter(views);root.setRight(scroll);root.setBottom(bottom);
        BorderPane.setMargin(scroll,new Insets(0,0,0,12));root.setStyle("-fx-font-family: sans-serif; -fx-background-color: #eef2f6;");
        stage.setTitle("Эволюция сотрудничества — лаборатория");stage.setScene(new Scene(root,1180,930));stage.setMinWidth(940);stage.setMinHeight(720);
        controls();stage.show();
        profile.choose(Path.of(getParameters().getNamed().getOrDefault("profile","profiles/demo.properties")));
        String replay=getParameters().getNamed().get("replay");
        if(replay==null) newRun();
        else submit(()->replace(RecordedHistory.open(Path.of(replay))),next->{
            editedConfig=next.metadata().config();seed.setText(""+next.metadata().seed());pairing.setValue(next.metadata().pairing());apply(next);
        });
        if(getParameters().getNamed().containsKey("smoke-dir")) smoke(views);
    }

    private TitledPane genotypeLegend() {
        var grid=new GridPane();grid.setHgap(14);grid.setVgap(4);
        for(int i=0;i<32;i++) {
            String key=String.format(Locale.ROOT,"%5s",Integer.toBinaryString(i)).replace(' ','0').replace('0','C').replace('1','D');
            var circle=new javafx.scene.shape.Circle(5,WorldCanvas.genotypeColor(key));
            grid.add(new HBox(5,circle,new Label(key)),i%2,i/2);
        }
        var pane=new TitledPane("Все 32 генотипа",grid);pane.setExpanded(false);return pane;
    }

    private FileChooser chooser(String title,String description,String pattern) {
        var c=new FileChooser();c.setTitle(title);c.getExtensionFilters().add(new FileChooser.ExtensionFilter(description,pattern));return c;
    }
    private void resize(StackPane map) {world.resizeSquare(Math.max(1,Math.min(map.getWidth(),map.getHeight())));}
    private RecordedHistory.View replace(RecordedHistory next) throws Exception {
        var previous=history;history=next;
        if(previous!=null) previous.close();
        return history.view();
    }
    private void newRun() {
        if(worker.busy()) return;
        final long value;
        try {value=Long.parseLong(seed.getText().strip());}
        catch(NumberFormatException e) {message.setText("Seed: введите целое число от −9223372036854775808 до 9223372036854775807.");return;}
        pause();var path=profile.selectedPath();var changed=editedConfig;var mode=pairing.getValue();
        submit(()->replace(RecordedHistory.create(Path.of("results/replays"),changed!=null?changed:ConfigProfiles.load(path),value,mode)),this::apply);
    }
    private void navigate(Runnable action) {pause();submit(()->{action.run();return history.view();},this::apply);}
    private void advance() {
        if(view==null || worker.busy() || !view.canStepForward()) return;
        submit(()->{
            try {history.stepForward();}
            catch(java.io.IOException e) {if(history.view().failure()==null) throw e;}
            return history.view();
        },this::apply);
    }
    private <T> void submit(Callable<T> action,Consumer<T> done) {
        if(worker.submit(action,(result,error)->{
            if(error!=null) {pause();message.setText("Ошибка: "+error.getMessage());}
            else {message.setText("");done.accept(result);}
            controls();
        })) controls();
    }
    private void pause() {playing=false;timer.pause();controls();}
    private void apply(RecordedHistory.View next) {
        view=next;var s=next.snapshot();var c=next.metadata().config();var m=s.metrics();var g=m.generationMetrics();var t=m.lastTick();
        if(!next.canStepForward()) pause();
        heading.setText("Поколение "+s.generation()+" / "+c.maximumGenerations()+" · Такт "+s.tick()+" / "+c.ticksPerGeneration());
        parameters.setText(String.format(Locale.ROOT,"Активный мир: %d×%d · %d агентов · %d раундов · %s\nЭнергия: старт %.2f, ресурс %.2f, стоимость %.2f, за балл игры %.2f · ресурсы %.2f%% · мутация %.2f%% · seed %d%s",
                c.sideLength(),c.sideLength(),c.populationSize(),c.roundsPerMatch(),pairing.getConverter().toString(next.metadata().pairing()),
                c.initialEnergyHundredths()/100.,c.resourceEnergyHundredths()/100.,c.livingCostHundredths()/100.,c.gamePayoffWeightHundredths()/100.,
                100*c.resourceSpawnProbability(),100*c.mutationProbability(),s.seed(),next.readOnly()?" · Просмотр записи":""));
        String status=switch(s.status()) {case "completed"->"Запуск завершён";case "extinct"->"Популяция вымерла";default->"В процессе";};
        metrics.setText("СЕЙЧАС\nЖивых: "+m.livingAgents()+" / "+c.populationSize()+"\nВыживаемость: "+percent(g.survivalFraction())
                +"\nЭнергия: средняя "+energy(m.averageEnergyHundredths())+", макс. "+energy(g.maximumEnergyHundredths())
                +"\nРесурсов на карте: "+m.resourceCount()+"\nКластеризация генотипов: "+percent(g.spatialClustering())
                +"\n\nПОСЛЕДНИЙ ТАКТ"+(s.tick()==0?" (ещё не выполнен)":"")
                +"\nВстреч: "+t.gameStatistics().matches()+"\nСотрудничество C: "+percent(t.gameStatistics().cooperationRate())
                +"\nЭнергия от ресурсов: +"+energy(t.resourceEnergyHundredths())
                +"\nЭнергия от игры: +"+energy(t.gamePayoff()*(long)c.gamePayoffWeightHundredths())+"\nПогибло: "+t.deaths()
                +"\n\nПОКОЛЕНИЕ — НАКОПЛЕНО\nВстреч: "+g.gameStatistics().matches()
                +"\nC в первых раундах: "+percent(g.gameStatistics().firstRoundCooperationRate())
                +"\nC во всех раундах: "+percent(g.gameStatistics().cooperationRate())
                +"\nЭнергия от ресурсов: +"+energy(g.resourceEnergyHundredths())
                +"\nЭнергия от игры: +"+energy(g.gameEnergyHundredths())+"\n\n"+status);
        world.show(s);refreshCharts();
        savedTick.setText("Кадр "+s.elapsedTicks()+" из "+(next.size()-1)+" · Все кадры сохранены: "+next.path()
                +"\nГрафики: до 300 кадров перед выбранным тактом. "+(next.readOnly()?"Запись открыта для просмотра; новый запуск — отдельная симуляция.":"Новый запуск оставит эту запись на диске.")
                +(next.recovered()?" Восстановлена запись: незавершённый последний кадр пропущен.":"")
                +(next.failure()!=null?" "+next.failure():""));
        if(next.failure()!=null) {pause();message.setText(next.failure());}
        refreshing=true;
        try {slider.setMax(Math.max(1,next.size()-1));slider.setValue(next.cursor());}finally {refreshing=false;}
        controls();
    }
    private void refreshCharts() {
        if(view!=null && chartsTab.isSelected()) charts.show(MetricTimeline.fromSnapshots(view.window(),view.window().size()-1));
    }
    private void controls() {
        boolean busy=worker.busy(),empty=view==null;
        for(var b:new Button[]{newRun,demo,edit,openProfile,openReplay}) b.setDisable(busy);
        profile.setDisable(busy);pairing.setDisable(busy);seed.setDisable(busy);
        for(var b:new Button[]{saveProfile,saveReplay,export}) b.setDisable(busy || empty);
        start.setDisable(busy || empty || view.cursor()==0);back.setDisable(start.isDisabled());
        end.setDisable(busy || empty || view.cursor()==view.size()-1);
        forward.setDisable(busy || empty || !view.canStepForward());slider.setDisable(busy || empty || view.size()<2);
        play.setDisable(empty || !playing && !view.canStepForward());play.setText(playing?"Пауза":"Воспроизвести");
        if(busy) heading.setText("Вычисление / работа с записью…");
        else if(view!=null) heading.setText("Поколение "+view.snapshot().generation()+" / "+view.metadata().config().maximumGenerations()
                +" · Такт "+view.snapshot().tick()+" / "+view.metadata().config().ticksPerGeneration());
    }
    private String energy(double value) {return Double.isNaN(value)?"—":String.format(Locale.ROOT,"%.2f",value/100);}
    private String percent(double value) {return Double.isNaN(value)?"—":String.format(Locale.ROOT,"%.1f%%",value*100);}
    @Override public void stop() {
        timer.stop();worker.closeAfter(()->{if(history!=null) try {history.close();}catch(Exception e) {e.printStackTrace();}});
    }

    /** Runs actual controls asynchronously; a heartbeat proves JavaFX can run while the worker is occupied. */
    private void smoke(TabPane tabs) {
        Path out=Path.of(getParameters().getNamed().get("smoke-dir"));
        var poll=new Timeline(new KeyFrame(Duration.millis(30),e->{}));poll.setCycleCount(Animation.INDEFINITE);
        int[] state={0};long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(90);
        poll.getKeyFrames().setAll(new KeyFrame(Duration.millis(30),event->{
            try {
                if(System.nanoTime()>deadline) throw new IllegalStateException("UI smoke timed out");
                if(worker.busy()) return;
                if(!message.getText().isEmpty() && message.getText().startsWith("Ошибка")) throw new IllegalStateException(message.getText());
                switch(state[0]++) {
                    case 0 -> {
                        if(view==null || view.metadata().config().sideLength()!=20) throw new IllegalStateException("Demo startup");
                        Files.createDirectory(out);
                        // Exercise the actual editor and validation without a human dialog response.
                        var dialog=new ParameterEditor(stage,view.metadata().config());dialog.show();
                        if(dialog.getDialogPane().lookupAll(".text-field").size()!=13) throw new IllegalStateException("Editor fields");
                        var applyType=dialog.getDialogPane().getButtonTypes().stream()
                                .filter(b->b.getButtonData()==ButtonBar.ButtonData.OK_DONE).findFirst().orElseThrow();
                        var applyButton=(Button)dialog.getDialogPane().lookupButton(applyType);
                        ((TextField)dialog.getDialogPane().lookup("#initial")).setText("0.001");applyButton.fire();
                        if(!dialog.isShowing()) throw new IllegalStateException("Invalid editor value accepted");
                        ((TextField)dialog.getDialogPane().lookup("#initial")).setText("10");
                        ((TextField)dialog.getDialogPane().lookup("#ticks")).setText("400");
                        ((TextField)dialog.getDialogPane().lookup("#cost")).setText("0");
                        ((TextField)dialog.getDialogPane().lookup("#generations")).setText("2");
                        saveNode(dialog.getDialogPane(),out.resolve("editor-ui.png"));
                        applyButton.fire();editedConfig=dialog.getResult();
                        if(editedConfig==null || editedConfig.ticksPerGeneration()!=400) throw new IllegalStateException("Editor apply failed");
                        newRun();
                    }
                    case 1 -> submit(()->{for(int i=0;i<320;i++) history.stepForward();return history.view();},this::apply);
                    case 2 -> {
                        if(view.size()!=321 || view.window().size()!=300) throw new IllegalStateException("Full history / bounded window");
                        tabs.getSelectionModel().select(chartsTab);refreshCharts();saveScene(out.resolve("charts-ui.png"));start.fire();
                    }
                    case 3 -> {
                        if(view.snapshot().elapsedTicks()!=0) throw new IllegalStateException("Beginning lost");
                        end.fire();
                    }
                    case 4 -> {
                        if(view.snapshot().elapsedTicks()!=320) throw new IllegalStateException("Seek end");
                        tabs.getSelectionModel().selectFirst();saveScene(out.resolve("world-ui.png"));
                        var snapshot=view.snapshot();
                        submit(()->{history.copyTo(out.resolve("saved.eoc"));SnapshotExporter.write(out.resolve("frame.png"),snapshot,"smoke");
                            return replace(RecordedHistory.open(out.resolve("saved.eoc")));},this::apply);
                    }
                    case 5 -> {
                        if(!view.readOnly() || view.size()!=321) throw new IllegalStateException("Reopen recording");
                        // Hold the worker while a real FX pulse completes, without Thread.sleep on FX.
                        var release=new java.util.concurrent.CountDownLatch(1);
                        var heartbeat=new PauseTransition(Duration.millis(80));
                        heartbeat.setOnFinished(e->release.countDown());heartbeat.play();
                        submit(()->{if(!release.await(3,java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("FX heartbeat blocked");return history.view();},this::apply);
                    }
                    case 6 -> {speed.setValue(50);play.fire();}
                    case 7 -> {
                        if(view.snapshot().elapsedTicks()==0) {state[0]--;return;}
                        play.fire();
                        if(playing || view.snapshot().elapsedTicks()==0) throw new IllegalStateException("Replay play/pause");
                        seed.setText("invalid");newRun();if(!message.getText().startsWith("Seed:")) throw new IllegalStateException("Seed validation");
                        Files.writeString(out.resolve("success.txt"),"PASS: demo, editor, 320 frames, bounded cache, full seek, reopen, PNG/SVG, background worker + FX heartbeat, replay play/pause, seed validation\n");
                        poll.stop();stage.close();Platform.exit();
                    }
                }
            }catch(Exception e) {e.printStackTrace();poll.stop();System.exit(1);}
        }));poll.play();
    }
    private void saveScene(Path file) throws Exception {
        stage.getScene().getRoot().applyCss();stage.getScene().getRoot().layout();
        saveNode(stage.getScene().getRoot(),file);
    }
    private void saveNode(javafx.scene.Node node,Path file) throws Exception {
        var fx=node.snapshot(null,null);
        var image=new java.awt.image.BufferedImage((int)fx.getWidth(),(int)fx.getHeight(),java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<image.getHeight();y++) for(int x=0;x<image.getWidth();x++) image.setRGB(x,y,fx.getPixelReader().getArgb(x,y));
        javax.imageio.ImageIO.write(image,"png",file.toFile());
    }
}
