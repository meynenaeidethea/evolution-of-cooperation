package evolution.simulation;

import evolution.game.MatchPayoff;
import evolution.game.RepeatedGame;
import evolution.world.Agent;
import evolution.world.CellPosition;
import evolution.world.WorldGrid;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

public final class SimulationRunner {

    public SimulationRunResult run(SimulationConfig config, long seed) {
        SimulationSession session = new SimulationSession(config, seed);
        while (!session.isFinished()) {
            session.advance();
        }
        return session.result();
    }

    long createInitialPopulation(
            WorldGrid grid, SimulationConfig config, RandomGenerator random, long nextAgentId
    ) {
        List<CellPosition> positions = allPositions(config.sideLength());
        shuffle(positions, random);
        for (int index = 0; index < config.populationSize(); index++) {
            grid.placeAgent(new Agent(
                    nextAgentId++, positions.get(index),
                    config.initialEnergyHundredths(), Genetics.randomGenotype(random)
            ));
        }
        grid.regrowResources(config.resourceSpawnProbability(), random);
        return nextAgentId;
    }

    long createNextGeneration(
            WorldGrid grid, SimulationConfig config, RandomGenerator random, long nextAgentId
    ) {
        List<Agent> survivors = grid.livingAgents();
        List<CellPosition> positions = allPositions(config.sideLength());
        shuffle(positions, random);
        grid.clearAgents();
        grid.clearResources();

        for (int index = 0; index < config.populationSize(); index++) {
            Agent first = Genetics.selectTournament(survivors, config.tournamentSize(), random);
            Agent second = Genetics.selectTournament(survivors, config.tournamentSize(), random);
            grid.placeAgent(new Agent(
                    nextAgentId++, positions.get(index), config.initialEnergyHundredths(),
                    Genetics.childGenotype(first.genotype(), second.genotype(), config, random)
            ));
        }
        grid.regrowResources(config.resourceSpawnProbability(), random);
        return nextAgentId;
    }

    Map<Agent, Integer> playPairs(
            WorldGrid grid, SimulationConfig config, RandomGenerator random
    ) {
        Map<Agent, Integer> payoffs = new LinkedHashMap<>();
        for (AgentPair pair : PairingPhase.formPairs(grid, random)) {
            MatchPayoff payoff = RepeatedGame.play(
                    pair.first().genotype(), pair.second().genotype(), config.roundsPerMatch()
            );
            payoffs.put(pair.first(), payoff.firstAgentTotal());
            payoffs.put(pair.second(), payoff.secondAgentTotal());
        }
        return payoffs;
    }

    GenerationMetrics metricsFor(
            int generation, WorldGrid grid, int resourceEnergy, int gamePayoff
    ) {
        List<Agent> survivors = grid.livingAgents();
        double averageEnergy = survivors.stream().mapToInt(Agent::energyHundredths)
                .average().orElse(Double.NaN);
        Map<String, Integer> genotypes = new LinkedHashMap<>();
        survivors.stream()
                .sorted(Comparator.comparingLong(Agent::agentId))
                .forEach(agent -> genotypes.merge(genotypeKey(agent), 1, Integer::sum));
        return new GenerationMetrics(
                generation, survivors.size(), averageEnergy, resourceEnergy, gamePayoff,
                Map.copyOf(genotypes)
        );
    }

    private String genotypeKey(Agent agent) {
        var genotype = agent.genotype();
        return "" + genotype.firstMove() + genotype.afterCC() + genotype.afterCD()
                + genotype.afterDC() + genotype.afterDD();
    }

    private List<CellPosition> allPositions(int sideLength) {
        List<CellPosition> positions = new ArrayList<>(sideLength * sideLength);
        for (int row = 0; row < sideLength; row++) {
            for (int column = 0; column < sideLength; column++) {
                positions.add(new CellPosition(row, column));
            }
        }
        return positions;
    }

    private <T> void shuffle(List<T> values, RandomGenerator random) {
        for (int index = values.size() - 1; index > 0; index--) {
            int other = random.nextInt(index + 1);
            T value = values.get(index);
            values.set(index, values.get(other));
            values.set(other, value);
        }
    }
}
