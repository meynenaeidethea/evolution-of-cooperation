package evolution.ui;

import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

import java.nio.file.Path;

/** A selection retains the actual path, including custom files with a built-in filename. */
final class ProfileSelector extends HBox {
    private record Choice(Path path, String label) {
        @Override public String toString() { return label; }
    }

    private final ComboBox<Choice> choices = new ComboBox<>();

    ProfileSelector() {
        super(10);
        add(Path.of("profiles/demo.properties"), "Демонстрация");
        add(Path.of("profiles/user.properties"), "Пользовательский");
        add(Path.of("profiles/research.properties"), "Исследование");
        choices.setPrefWidth(240);
        choices.setMaxWidth(280);
        choices.getSelectionModel().selectFirst();
        getChildren().addAll(new Label("Профиль:"), choices);
    }

    private void add(Path path, String label) {
        choices.getItems().add(new Choice(path.toAbsolutePath().normalize(), label));
    }

    Path selectedPath() { return choices.getValue().path(); }

    void onChange(Runnable listener) {choices.valueProperty().addListener((o,a,b)->listener.run());}

    void choose(Path path) {
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

}
