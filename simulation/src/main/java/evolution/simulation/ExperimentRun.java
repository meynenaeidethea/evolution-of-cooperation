package evolution.simulation;

import java.util.Objects;

/** Scenario and repetition identify a run independently of its random seed. */
public record ExperimentRun(String scenarioId, int repetition, SimulationRunResult result) {
    public ExperimentRun {
        Objects.requireNonNull(scenarioId, "Scenario ID must be defined");
        Objects.requireNonNull(result, "Run result must be defined");
        if (scenarioId.isBlank()) throw new IllegalArgumentException("Scenario ID must not be blank");
        if (repetition < 1) throw new IllegalArgumentException("Repetition must be positive");
    }
}
