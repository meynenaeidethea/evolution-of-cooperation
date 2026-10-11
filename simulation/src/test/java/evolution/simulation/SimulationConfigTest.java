package evolution.simulation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SimulationConfigTest {

    @Test
    void createsApprovedBaselineConfiguration() {
        SimulationConfig config = SimulationConfig.baseline();

        assertEquals(30, config.sideLength());
        assertEquals(450, config.populationSize());
        assertEquals(5, config.roundsPerMatch());
        assertEquals(1_000, config.initialEnergyHundredths());
        assertEquals(20, config.gamePayoffWeightHundredths());
        assertEquals(100, config.maximumGenerations());
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