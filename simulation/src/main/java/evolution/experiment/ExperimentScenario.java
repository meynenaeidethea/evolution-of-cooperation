package evolution.experiment;

import evolution.simulation.SimulationConfig;
import evolution.simulation.PairingMode;

import java.util.Objects;

public record ExperimentScenario(String id, SimulationConfig config, PairingMode pairingMode) {
    public ExperimentScenario(String id, SimulationConfig config) { this(id, config, PairingMode.LOCAL); }

    public ExperimentScenario {
        Objects.requireNonNull(id, "Scenario ID must be defined");
        Objects.requireNonNull(config, "Scenario configuration must be defined");
        Objects.requireNonNull(pairingMode, "Pairing mode must be defined");
        if (!id.matches("[a-z0-9][a-z0-9-]*")) {
            throw new IllegalArgumentException("Scenario ID must contain lowercase letters, digits or hyphens");
        }
    }
}
