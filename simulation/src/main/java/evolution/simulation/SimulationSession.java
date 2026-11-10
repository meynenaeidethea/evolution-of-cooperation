package evolution.simulation;

import evolution.game.GameStatistics;
import evolution.world.Agent;
import evolution.world.CellPosition;
import evolution.world.WorldGrid;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

/** A forward-only run that owns its mutable world and exposes only detached snapshots. */
public final class SimulationSession {
    public static final String MODEL_VERSION = "genetic-v2-controls";
    public static final String RANDOM_GENERATOR = "L64X128MixRandom";
    private final SimulationConfig config;
    private final PairingMode pairingMode;
    private final long seed;
    private final RandomGenerator random;
    private final WorldGrid grid;
    private final SimulationRunner phases = new SimulationRunner();
    private final List<GenerationMetrics> metrics = new ArrayList<>();
    private long nextAgentId;
    private long elapsedTicks;
    private int generation = 1;
    private int tick;
    private long resourceEnergy;
    private long gamePayoff;
    private long agentTicks;
    private final long[] reactionUses = new long[4];
    private GameStatistics gameStatistics = GameStatistics.EMPTY;
    private SimulationSnapshot.TickMetrics lastTick = SimulationSnapshot.TickMetrics.EMPTY;
    private String status = "running";

    public SimulationSession(SimulationConfig config, long seed) {
        this(config, seed, PairingMode.LOCAL);
    }

    public SimulationSession(SimulationConfig config, long seed, PairingMode pairingMode) {
        this.config = Objects.requireNonNull(config);
        this.pairingMode = Objects.requireNonNull(pairingMode);
        this.seed = seed;
        random = RandomGeneratorFactory.of(RANDOM_GENERATOR).create(seed);
        grid = new WorldGrid(config.sideLength());
        nextAgentId = phases.createInitialPopulation(grid, config, random, 1L);
    }

    public long elapsedTicks() { return elapsedTicks; }

    public boolean isFinished() {
        return !status.equals("running");
    }

    /** Exactly one tick; reproduction precedes the first tick of the next generation. */
    public void advance() {
        if (isFinished()) {
            throw new IllegalStateException("Run is already finished");
        }
        if (tick == config.ticksPerGeneration()) {
            nextAgentId = phases.createNextGeneration(grid, config, random, nextAgentId);
            generation++;
            tick = 0;
            resourceEnergy = 0;
            gamePayoff = 0;
            agentTicks = 0;
            java.util.Arrays.fill(reactionUses, 0);
            gameStatistics = GameStatistics.EMPTY;
        }
        grid.regrowResources(config.resourceSpawnProbability(), random);
        var intents = MovementPhase.chooseIntents(grid, random);
        MovementPhase.resolveAndApply(grid, intents, random);
        Map<Agent, Integer> resources = EnergyPhase.collectResourceEnergy(
                grid, config.resourceEnergyHundredths());
        long tickResources = resources.values().stream().mapToLong(Integer::longValue).sum();
        resourceEnergy += tickResources;
        agentTicks += grid.livingAgents().size();
        var games = phases.playPairs(grid, config, random, pairingMode);
        long tickPayoff = games.payoffs().values().stream().mapToLong(Integer::longValue).sum();
        gamePayoff += tickPayoff;
        gameStatistics = gameStatistics.plus(games.statistics());
        for (int i = 0; i < 4; i++) reactionUses[i] += games.reactionUses()[i];
        int beforeDeaths = grid.livingAgents().size();
        EnergyPhase.applyEnergyAndRemoveDead(grid, resources, games.payoffs(), config);
        lastTick = new SimulationSnapshot.TickMetrics(tickResources, tickPayoff,
                games.statistics(), beforeDeaths - grid.livingAgents().size());
        tick++;
        elapsedTicks++;
        if (grid.livingAgents().isEmpty() || tick == config.ticksPerGeneration()) {
            metrics.add(currentMetrics());
            if (grid.livingAgents().isEmpty()) {
                status = "extinct";
            } else if (generation == config.maximumGenerations()) {
                status = "completed";
            }
        }
    }

    public SimulationSnapshot currentSnapshot() {
        List<SimulationSnapshot.AgentState> agents = grid.livingAgents().stream()
                .map(agent -> new SimulationSnapshot.AgentState(agent.agentId(), agent.position(),
                        agent.energyHundredths(), agent.genotype())).toList();
        List<CellPosition> resources = new ArrayList<>();
        for (int row = 0; row < grid.sideLength(); row++) {
            for (int column = 0; column < grid.sideLength(); column++) {
                CellPosition position = new CellPosition(row, column);
                if (grid.hasResourceAt(position)) {
                    resources.add(position);
                }
            }
        }
        double average = agents.stream().mapToInt(SimulationSnapshot.AgentState::energyHundredths)
                .average().orElse(Double.NaN);
        return new SimulationSnapshot(config.sideLength(), seed, generation, tick, elapsedTicks,
                status, agents, resources, new SimulationSnapshot.Metrics(agents.size(), average,
                resources.size(), resourceEnergy, gamePayoff, currentMetrics(), lastTick));
    }

    private GenerationMetrics currentMetrics() {
        return MetricsCalculator.measure(generation, tick, grid, config, resourceEnergy, gamePayoff,
                gameStatistics, agentTicks, reactionUses);
    }

    public SimulationRunResult result() {
        if (!isFinished()) {
            throw new IllegalStateException("Run is not finished");
        }
        return new SimulationRunResult(config, seed, status, List.copyOf(metrics), MODEL_VERSION,
                RANDOM_GENERATOR, Runtime.version().toString(), pairingMode);
    }
}
