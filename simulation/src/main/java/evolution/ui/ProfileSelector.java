package evolution.ui;

import evolution.simulation.ConfigProfiles;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Consumer;

/** A selection retains the actual path, including custom files with a built-in filename. */
final class ProfileSelector extends HBox {
    private record Choice(Path path, String label) {
        @Override public String toString() { return label; }
    }

    private final ComboBox<Choice> choices = new ComboBox<>();

    ProfileSelector() {
        super(10);
        add(Path.of("profiles/user.properties"), "Пользовательский");
        add(Path.of("profiles/research.properties"), "Исследование");
        add(Path.of("profiles/demo.properties"), "Демонстрация");
        choices.setPrefWidth(240);
        choices.setMaxWidth(280);
        choices.getSelectionModel().selectFirst();
        getChildren().addAll(new Label("Профиль:"), choices);
    }

    private void add(Path path, String label) {
        choices.getItems().add(new Choice(path.toAbsolutePath().normalize(), label));
    }

    Path selectedPath() { return choices.getValue().path(); }

    void select(Path path) throws IOException {
        ConfigProfiles.load(path); // Validate before changing the visible selection.
        Path absolute = path.toAbsolutePath().normalize();
        Choice choice = choices.getItems().stream().filter(c -> c.path().equals(absolute)).findFirst()
                .orElseGet(() -> {
                    Choice custom = new Choice(absolute, absolute.toString());
                    choices.getItems().add(custom);
                    return custom;
                });
        choices.setValue(choice);
        choices.setTooltip(new javafx.scene.control.Tooltip(absolute.toString()));
    }

    Button openButton(Window owner, Runnable startRun, Consumer<String> error) {
        Button open = new Button("Открыть профиль…");
        open.setOnAction(event -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Выберите профиль симуляции");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Профили (*.properties)", "*.properties"));
            var file = chooser.showOpenDialog(owner);
            if (file == null) return;
            try {
                select(file.toPath());
                startRun.run();
            } catch (IOException | IllegalArgumentException e) {
                error.accept("Ошибка профиля: " + e.getMessage());
            }
        });
        return open;
    }
}
