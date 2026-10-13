package evolution.simulation;

import evolution.game.GameStatistics;
import evolution.world.Agent;
import evolution.world.WorldGrid;

import java.util.LinkedHashMap;

/** Read-only measurement: never draws random numbers or changes the world. */
final class MetricsCalculator {
    private MetricsCalculator() {}

    static GenerationMetrics measure(int generation, int ticks, WorldGrid grid,
                                     SimulationConfig config, long resources, long payoff,
                                     GameStatistics games) {
        var agents = grid.livingAgents();
        double average = agents.stream().mapToInt(Agent::energyHundredths).average().orElse(Double.NaN);
        double maximum = agents.stream().mapToDouble(Agent::energyHundredths).max().orElse(Double.NaN);
        var genotypes = new LinkedHashMap<String, Integer>();
        for (var agent : agents) {
            var g = agent.genotype();
            String key = "" + g.firstMove() + g.afterCC() + g.afterCD() + g.afterDC() + g.afterDD();
            genotypes.merge(key, 1, Integer::sum);
        }
        return new GenerationMetrics(generation, agents.size(), average, resources, payoff, genotypes,
                ticks, (double) agents.size() / config.populationSize(), maximum, games,
                (double) resources / config.populationSize(), (double) payoff / config.populationSize(),
                config.gamePayoffWeightHundredths() * payoff, spatialClustering(grid), agents.isEmpty());
    }

    static double spatialClustering(WorldGrid grid) {
        long pairs = 0;
        long same = 0;
        // Right, down, down-right, down-left count every unordered Moore pair once, including wrap.
        int[][] directions = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};
        for (var agent : grid.livingAgents()) {
            for (var direction : directions) {
                var position = agent.position().moveBy(direction[0], direction[1], grid.sideLength());
                var neighbour = grid.agentAt(position).filter(Agent::isAlive);
                if (neighbour.isPresent()) {
                    pairs++;
                    if (agent.genotype().equals(neighbour.get().genotype())) same++;
                }
            }
        }
        return pairs == 0 ? Double.NaN : (double) same / pairs;
    }
}
