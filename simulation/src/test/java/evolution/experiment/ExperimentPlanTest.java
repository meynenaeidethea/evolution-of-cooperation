package evolution.experiment;

import evolution.simulation.SimulationConfig;
import evolution.simulation.PairingMode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class ExperimentPlanTest {
    // Deliberately different from the suggested parameters, including values outside the sweep points.
    private final SimulationConfig base = new SimulationConfig(7, 10, 17, 7, 1234, 345, 67,
            0.07, 17, 0.6, 0.03, 2, 4);

    @Test
    void everyVariantChangesExactlyOneFactorAndKeepsUserParameters() {
        for (var series : ExperimentPlan.Series.values()) {
            var scenarios = ExperimentPlan.scenarios(base, series);
            assertSame(base, scenarios.getFirst().config());
            assertEquals("baseline", scenarios.getFirst().id());
            for (var scenario : scenarios.subList(1, scenarios.size())) {
                var config = scenario.config();
                assertEquals(base.sideLength(), config.sideLength());
                assertEquals(base.populationSize(), config.populationSize());
                assertEquals(base.ticksPerGeneration(), config.ticksPerGeneration());
                assertEquals(base.initialEnergyHundredths(), config.initialEnergyHundredths());
                assertEquals(base.livingCostHundredths(), config.livingCostHundredths());
                assertEquals(base.crossoverProbability(), config.crossoverProbability());
                assertEquals(base.tournamentSize(), config.tournamentSize());
                assertEquals(base.maximumGenerations(), config.maximumGenerations());
                int differences = 0;
                if (base.roundsPerMatch() != config.roundsPerMatch()) differences++;
                if (base.gamePayoffWeightHundredths() != config.gamePayoffWeightHundredths()) differences++;
                if (base.mutationProbability() != config.mutationProbability()) differences++;
                if (base.resourceSpawnProbability() != config.resourceSpawnProbability()) differences++;
                if (base.resourceEnergyHundredths() != config.resourceEnergyHundredths()) differences++;
                if (scenario.pairingMode() != PairingMode.LOCAL) differences++;
                assertEquals(1, differences, scenario.id());
            }
        }
    }

    @Test
    void sweepsContainThePlannedComparisonPoints() {
        assertEquals(Set.of(1, 3, 5, 7, 10), ExperimentPlan.scenarios(base, ExperimentPlan.Series.ROUNDS)
                .stream().map(s -> s.config().roundsPerMatch()).collect(Collectors.toSet()));
        assertEquals(Set.of(0, 10, 17, 20, 35, 50), ExperimentPlan.scenarios(base, ExperimentPlan.Series.GAME_WEIGHT)
                .stream().map(s -> s.config().gamePayoffWeightHundredths()).collect(Collectors.toSet()));
        assertEquals(Set.of(0.0, 0.005, 0.01, 0.02, 0.03, 0.05), ExperimentPlan.scenarios(base, ExperimentPlan.Series.MUTATION)
                .stream().map(s -> s.config().mutationProbability()).collect(Collectors.toSet()));
        assertEquals(Set.of(0.02, 0.05, 0.07, 0.1, 0.25), ExperimentPlan.scenarios(base, ExperimentPlan.Series.RESOURCE_SPAWN)
                .stream().map(s -> s.config().resourceSpawnProbability()).collect(Collectors.toSet()));
        assertEquals(Set.of(200, 345, 400, 800), ExperimentPlan.scenarios(base, ExperimentPlan.Series.RESOURCE_ENERGY)
                .stream().map(s -> s.config().resourceEnergyHundredths()).collect(Collectors.toSet()));
    }

    @Test
    void baselineIsNotDuplicatedWhenItAlreadyMatchesSweepPoints() {
        var reference = new SimulationConfig(3, 4, 2, 5, 1000, 400, 100,
                0.1, 20, 0.8, 0.01, 3, 2);
        var all = ExperimentPlan.scenarios(reference, ExperimentPlan.Series.ALL);
        assertEquals(18, all.size());
        assertEquals(1, all.stream().filter(s -> s.config().equals(reference) && s.pairingMode() == PairingMode.LOCAL).count());
        assertEquals(all.size(), all.stream().map(ExperimentScenario::id).distinct().count());
        assertEquals(all.size() - 1, all.stream().map(ExperimentScenario::config).distinct().count());
        assertEquals(all, ExperimentPlan.scenarios(reference, ExperimentPlan.Series.ALL));
        assertThrows(UnsupportedOperationException.class, all::clear);
    }

    @Test
    void acceptsOnlyKnownSeriesAndSafeScenarioIds() {
        for (var series : ExperimentPlan.Series.values()) assertEquals(series, ExperimentPlan.Series.parse(series.option()));
        assertThrows(IllegalArgumentException.class, () -> ExperimentPlan.Series.parse("unknown"));
        assertThrows(IllegalArgumentException.class, () -> new ExperimentScenario("../results", base));
        assertThrows(IllegalArgumentException.class, () -> new ExperimentScenario("", base));
        assertEquals(List.of(new ExperimentScenario("baseline", base)), ExperimentPlan.scenarios(base, ExperimentPlan.Series.BASELINE));
    }
}
