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
import java.util.random.RandomGeneratorFactory;

public final class SimulationRunner {

    public SimulationRunResult run(SimulationConfig config, long seed) {
        RandomGenerator random = RandomGeneratorFactory
                .of("L64X128MixRandom")
                .create(seed);
        WorldGrid grid = new WorldGrid(config.sideLength());
        long nextAgentId = createInitialPopulation(grid, config, random, 1L);
        List<GenerationMetrics> metrics = new ArrayList<>();

        for (int generation = 1; generation <= config.maximumGenerations(); generation++) {
            int resourceEnergy = 0;
            int gamePayoff = 0;

            for (int tick = 0; tick < config.ticksPerGeneration()
                    && !grid.livingAgents().isEmpty(); tick++) {
                grid.regrowResources(config.resourceSpawnProbability(), random);
                var intents = MovementPhase.chooseIntents(grid, random);
                MovementPhase.resolveAndApply(grid, intents, random);

                Map<Agent, Integer> resources = EnergyPhase.collectResourceEnergy(
                        grid, config.resourceEnergyHundredths()
                );
                resourceEnergy += resources.values().stream()
                        .mapToInt(Integer::intValue).sum();

                Map<Agent, Integer> payoffs = playPairs(grid, config, random);
                gamePayoff += payoffs.values().stream().mapToInt(Integer::intValue).sum();
                EnergyPhase.applyEnergyAndRemoveDead(grid, resources, payoffs, config);
            }

            metrics.add(metricsFor(generation, grid, resourceEnergy, gamePayoff));
            if (grid.livingAgents().isEmpty()) {
                return new SimulationRunResult(config, seed, "extinct", List.copyOf(metrics));
            }
            if (generation < config.maximumGenerations()) {
                nextAgentId = createNextGeneration(grid, config, random, nextAgentId);
            }
        }

        return new SimulationRunResult(config, seed, "completed", List.copyOf(metrics));
    }

    private long createInitialPopulation(
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

    private long createNextGeneration(
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

    private Map<Agent, Integer> playPairs(
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

    private GenerationMetrics metricsFor(
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
