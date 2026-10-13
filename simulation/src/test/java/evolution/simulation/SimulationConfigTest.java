package evolution.simulation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SimulationConfigTest {

    @Test
    void initializesWorldUsingChosenBaselineConfiguration() {
        SimulationConfig config = SimulationConfig.baseline();
        var initial = new SimulationSession(config, 1).currentSnapshot();
        assertEquals(config.sideLength(), initial.sideLength());
        assertEquals(config.populationSize(), initial.agents().size());
        assertEquals(config.populationSize(), initial.agents().stream().map(a -> a.position()).distinct().count());
        for (var agent : initial.agents()) {
            assertEquals(config.initialEnergyHundredths(), agent.energyHundredths());
        }
    }

    @Test
    void rejectsPopulationLargerThanGridCapacity() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SimulationConfig(
                        3, 10, 50, 5, 1_000, 400, 100,
                        0.10, 20, 0.80, 0.01, 3, 100
                )
        );
    }

    @Test
    void rejectsProbabilityOutsideZeroToOneRange() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SimulationConfig(
                        30, 450, 50, 5, 1_000, 400, 100,
                        1.01, 20, 0.80, 0.01, 3, 100
                )
        );
    }

    @Test
    void rejectsNonPositiveMaximumGenerations() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SimulationConfig(
                        30, 450, 50, 5, 1_000, 400, 100,
                        0.10, 20, 0.80, 0.01, 3, 0
                )
        );
    }
}
