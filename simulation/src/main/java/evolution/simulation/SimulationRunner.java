package evolution.simulation;

import evolution.game.MatchStatistics;
import evolution.game.RepeatedGame;
import evolution.world.Agent;
import evolution.world.CellPosition;
import evolution.world.WorldGrid;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

public final class SimulationRunner {

    public SimulationRunResult run(SimulationConfig config, long seed) {
        return run("baseline", 1, config, seed, (generation, grid) -> {});
    }

    public SimulationRunResult run(String scenarioId, int repetition, SimulationConfig config, long seed,
            java.util.function.BiConsumer<Integer, WorldGrid> observer) {
        if (!scenarioId.matches("[a-zA-Z0-9_.-]+") || repetition < 1) {
            throw new IllegalArgumentException("Invalid scenario ID or repetition");
        }
        MatchStatistics[][] cache = new MatchStatistics[32][32];
        RandomGenerator random = RandomGeneratorFactory
                .of(SimulationRunResult.GENERATOR)
                .create(seed);
        WorldGrid grid = new WorldGrid(config.sideLength());
        long nextAgentId = createInitialPopulation(grid, config, random, 1L);
        List<GenerationMetrics> metrics = new ArrayList<>();

        for (int generation = 1; generation <= config.maximumGenerations(); generation++) {
            long resourceEnergy = 0, gamePayoff = 0;
            long[] counts = new long[7]; // matches, first C, all C, CC, CD, DC, DD
            int ticks = 0;
            for (; ticks < config.ticksPerGeneration() && !grid.livingAgents().isEmpty(); ticks++) {
                grid.regrowResources(config.resourceSpawnProbability(), random);
                var intents = MovementPhase.chooseIntents(grid, random);
                MovementPhase.resolveAndApply(grid, intents, random);

                Map<Agent, Integer> resources = EnergyPhase.collectResourceEnergy(
                        grid, config.resourceEnergyHundredths()
                );
                resourceEnergy += resources.values().stream()
                        .mapToLong(Integer::longValue).sum();

                Map<Agent, Integer> payoffs = playPairs(grid, config, random, cache, counts);
                gamePayoff += payoffs.values().stream().mapToLong(Integer::longValue).sum();
                EnergyPhase.applyEnergyAndRemoveDead(grid, resources, payoffs, config);
            }

            metrics.add(metricsFor(generation, ticks, grid, config, resourceEnergy, gamePayoff, counts));
            observer.accept(generation, grid);
            if (grid.livingAgents().isEmpty()) {
                return result(scenarioId, repetition, config, seed, "extinct", metrics);
            }
            if (generation < config.maximumGenerations()) {
                nextAgentId = createNextGeneration(grid, config, random, nextAgentId);
            }
        }

        return result(scenarioId, repetition, config, seed, "completed", metrics);
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

    private SimulationRunResult result(String scenario, int repetition, SimulationConfig config,
            long seed, String status, List<GenerationMetrics> metrics) {
        return new SimulationRunResult(scenario, repetition, config, seed, SimulationRunResult.MODEL_VERSION,
                SimulationRunResult.GENERATOR, System.getProperty("java.version"), status, metrics);
    }

    private Map<Agent, Integer> playPairs(WorldGrid grid, SimulationConfig config, RandomGenerator random,
            MatchStatistics[][] cache, long[] counts) {
        Map<Agent, Integer> payoffs = new LinkedHashMap<>();
        for (AgentPair pair : PairingPhase.formPairs(grid, random, config.pairingMode())) {
            int first = pair.first().genotype().index(), second = pair.second().genotype().index();
            MatchStatistics match = cache[first][second];
            if (match == null) {
                match = RepeatedGame.analyze(pair.first().genotype(), pair.second().genotype(), config.roundsPerMatch());
                cache[first][second] = match;
            }
            payoffs.put(pair.first(), match.payoff().firstAgentTotal());
            payoffs.put(pair.second(), match.payoff().secondAgentTotal());
            counts[0]++; counts[1] += match.firstRoundCooperations(); counts[2] += match.cooperations();
            counts[3] += match.cc(); counts[4] += match.cd(); counts[5] += match.dc(); counts[6] += match.dd();
        }
        return payoffs;
    }

    private GenerationMetrics metricsFor(int generation, int ticks, WorldGrid grid, SimulationConfig config,
            long resourceEnergy, long gamePayoff, long[] counts) {
        List<Agent> survivors = grid.livingAgents();
        double averageEnergy = survivors.stream().mapToInt(Agent::energyHundredths).average().orElse(Double.NaN);
        double maximumEnergy = survivors.stream().mapToInt(Agent::energyHundredths).max().stream()
                .mapToDouble(value -> value).findFirst().orElse(Double.NaN);
        Map<String, Integer> genotypes = new LinkedHashMap<>();
        survivors.forEach(agent -> genotypes.merge(agent.genotype().key(), 1, Integer::sum));
        return new GenerationMetrics(generation, ticks, survivors.size(),
                (double) survivors.size() / config.populationSize(), averageEnergy, maximumEnergy,
                resourceEnergy, gamePayoff, gamePayoff * config.gamePayoffWeightHundredths(),
                counts[0], counts[1], counts[2], counts[3], counts[4], counts[5], counts[6],
                clustering(grid), genotypes);
    }

    static double clustering(WorldGrid grid) {
        long pairs = 0, same = 0;
        int[][] directions = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};
        for (Agent agent : grid.livingAgents()) {
            for (int[] d : directions) {
                var neighbour = grid.agentAt(agent.position().moveBy(d[0], d[1], grid.sideLength()));
                if (neighbour.isPresent()) {
                    pairs++;
                    if (agent.genotype().equals(neighbour.get().genotype())) same++;
                }
            }
        }
        return GenerationMetrics.ratio(same, pairs);
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
