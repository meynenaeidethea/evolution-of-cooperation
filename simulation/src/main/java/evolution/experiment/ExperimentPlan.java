package evolution.experiment;

import evolution.simulation.SimulationConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** One-factor comparisons relative to the user's immutable baseline configuration. */
public final class ExperimentPlan {
    private ExperimentPlan() {}

    public enum Series {
        BASELINE, ROUNDS, GAME_WEIGHT, MUTATION, RESOURCE_SPAWN, RESOURCE_ENERGY, ALL;

        public String option() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }

        public static Series parse(String option) {
            for (var series : values()) {
                if (series.option().equals(option)) return series;
            }
            throw new IllegalArgumentException("Unknown series: " + option);
        }
    }

    public static List<ExperimentScenario> scenarios(SimulationConfig baseline, Series series) {
        Objects.requireNonNull(baseline);
        Objects.requireNonNull(series);
        var scenarios = new ArrayList<ExperimentScenario>();
        scenarios.add(new ExperimentScenario("baseline", baseline));
        if (series == Series.ROUNDS || series == Series.ALL) {
            for (int value : new int[]{1, 3, 5, 10}) {
                add(scenarios, baseline, "rounds-" + value,
                        copy(baseline, value, baseline.gamePayoffWeightHundredths(), baseline.mutationProbability(),
                                baseline.resourceSpawnProbability(), baseline.resourceEnergyHundredths()));
            }
        }
        if (series == Series.GAME_WEIGHT || series == Series.ALL) {
            for (int value : new int[]{0, 10, 20, 35, 50}) {
                add(scenarios, baseline, "game-weight-" + value,
                        copy(baseline, baseline.roundsPerMatch(), value, baseline.mutationProbability(),
                                baseline.resourceSpawnProbability(), baseline.resourceEnergyHundredths()));
            }
        }
        if (series == Series.MUTATION || series == Series.ALL) {
            for (int thousandths : new int[]{0, 5, 10, 20, 50}) {
                add(scenarios, baseline, "mutation-" + thousandths,
                        copy(baseline, baseline.roundsPerMatch(), baseline.gamePayoffWeightHundredths(),
                                thousandths / 1000.0, baseline.resourceSpawnProbability(), baseline.resourceEnergyHundredths()));
            }
        }
        if (series == Series.RESOURCE_SPAWN || series == Series.ALL) {
            for (int hundredths : new int[]{2, 5, 10, 25}) {
                add(scenarios, baseline, "resource-spawn-" + hundredths,
                        copy(baseline, baseline.roundsPerMatch(), baseline.gamePayoffWeightHundredths(),
                                baseline.mutationProbability(), hundredths / 100.0, baseline.resourceEnergyHundredths()));
            }
        }
        if (series == Series.RESOURCE_ENERGY || series == Series.ALL) {
            for (int value : new int[]{200, 400, 800}) {
                add(scenarios, baseline, "resource-energy-" + value,
                        copy(baseline, baseline.roundsPerMatch(), baseline.gamePayoffWeightHundredths(),
                                baseline.mutationProbability(), baseline.resourceSpawnProbability(), value));
            }
        }
        return List.copyOf(scenarios);
    }

    private static void add(List<ExperimentScenario> scenarios, SimulationConfig baseline,
                            String id, SimulationConfig config) {
        // A sweep value equal to the control is already represented by the single baseline scenario.
        if (!config.equals(baseline)) scenarios.add(new ExperimentScenario(id, config));
    }

    private static SimulationConfig copy(SimulationConfig base, int rounds, int weight,
                                         double mutation, double spawn, int resourceEnergy) {
        return new SimulationConfig(base.sideLength(), base.populationSize(), base.ticksPerGeneration(),
                rounds, base.initialEnergyHundredths(), resourceEnergy, base.livingCostHundredths(), spawn,
                weight, base.crossoverProbability(), mutation, base.tournamentSize(), base.maximumGenerations());
    }
}
