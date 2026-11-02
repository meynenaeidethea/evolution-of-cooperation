package evolution.experiment;

import evolution.simulation.PairingMode;
import evolution.simulation.SimulationConfig;
import java.util.List;

/** The original twelve interventions. Unchanged fields always come from the supplied profile. */
public final class StudyScenarios {
    private StudyScenarios() {}
    public static List<ExperimentScenario> create(SimulationConfig c) {
        return List.of(new ExperimentScenario("baseline", c),
                new ExperimentScenario("rounds-1", change(c,1,c.gamePayoffWeightHundredths(),c.mutationProbability(),c.resourceSpawnProbability())),
                new ExperimentScenario("rounds-10", change(c,10,c.gamePayoffWeightHundredths(),c.mutationProbability(),c.resourceSpawnProbability())),
                new ExperimentScenario("normalized-1", change(c,1,100,c.mutationProbability(),c.resourceSpawnProbability())),
                new ExperimentScenario("normalized-10", change(c,10,10,c.mutationProbability(),c.resourceSpawnProbability())),
                new ExperimentScenario("game-zero", change(c,c.roundsPerMatch(),0,c.mutationProbability(),c.resourceSpawnProbability())),
                new ExperimentScenario("mutation-zero", change(c,c.roundsPerMatch(),c.gamePayoffWeightHundredths(),0,c.resourceSpawnProbability())),
                new ExperimentScenario("mutation-high", change(c,c.roundsPerMatch(),c.gamePayoffWeightHundredths(),.05,c.resourceSpawnProbability())),
                new ExperimentScenario("resource-poor", change(c,c.roundsPerMatch(),c.gamePayoffWeightHundredths(),c.mutationProbability(),.02)),
                new ExperimentScenario("resource-rich", change(c,c.roundsPerMatch(),c.gamePayoffWeightHundredths(),c.mutationProbability(),.25)),
                new ExperimentScenario("global", c, PairingMode.GLOBAL),
                new ExperimentScenario("global-matched", c, PairingMode.GLOBAL_MATCHED));
    }
    private static SimulationConfig change(SimulationConfig c,int k,int alpha,double mu,double q) {
        return new SimulationConfig(c.sideLength(),c.populationSize(),c.ticksPerGeneration(),k,
                c.initialEnergyHundredths(),c.resourceEnergyHundredths(),c.livingCostHundredths(),q,
                alpha,c.crossoverProbability(),mu,c.tournamentSize(),c.maximumGenerations());
    }
}
