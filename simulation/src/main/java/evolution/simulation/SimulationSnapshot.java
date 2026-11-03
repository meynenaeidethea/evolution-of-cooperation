package evolution.simulation;

import evolution.game.Genotype;
import evolution.game.GameStatistics;
import evolution.world.CellPosition;

import java.util.List;

/** Detached rendering data; positions and genotypes are immutable records too. */
public record SimulationSnapshot(
        int sideLength, long seed, int generation, int tick, long elapsedTicks, String status,
        List<AgentState> agents, List<CellPosition> resources, Metrics metrics
) {
    public SimulationSnapshot {
        agents = List.copyOf(agents);
        resources = List.copyOf(resources);
    }

    public record AgentState(long agentId, CellPosition position, int energyHundredths,
                             Genotype genotype) {}

    /** Resource energy and raw game payoff accumulate within the displayed generation. */
    public record Metrics(int livingAgents, double averageEnergyHundredths, int resourceCount,
                          long resourceEnergyHundredths, long gamePayoff,
                          GenerationMetrics generationMetrics, TickMetrics lastTick) {}

    /** Flows during exactly the displayed tick, including games played by agents that died. */
    public record TickMetrics(long resourceEnergyHundredths, long gamePayoff,
                              GameStatistics gameStatistics, int deaths) {
        public static final TickMetrics EMPTY = new TickMetrics(0, 0, GameStatistics.EMPTY, 0);
    }
}
