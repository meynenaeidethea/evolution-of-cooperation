package evolution.ui;

import javafx.application.Application;

/** Plain launcher allows IntelliJ to run JavaFX from the Gradle runtime classpath. */
public final class  SimulationVisualMain {
    private SimulationVisualMain() {}

    public static void main(String[] args) {
        Application.launch(SimulationApplication.class, args);
    }
}
