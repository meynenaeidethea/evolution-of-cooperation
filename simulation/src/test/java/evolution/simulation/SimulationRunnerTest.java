package evolution.simulation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SimulationRunnerTest {

    @Test
    void repeatsExactlyWithSameSeed() {
        SimulationConfig config = new SimulationConfig(
                3, 4, 2, 1, 1_000, 0, 0,
                0.0, 0, 0.8, 0.01, 3, 2
        );
        SimulationRunner runner = new SimulationRunner();

        SimulationRunResult first = runner.run(config, 7L);
        SimulationRunResult second = runner.run(config, 7L);

        assertEquals(first, second);
        assertEquals("completed", first.status());
        assertEquals(2, first.generations().size());
    }
}
