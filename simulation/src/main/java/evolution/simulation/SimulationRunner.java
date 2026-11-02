package evolution.simulation;

import evolution.game.GameStatistics;
import evolution.game.RepeatedGame;
import evolution.world.Agent;
import evolution.world.CellPosition;
import evolution.world.WorldGrid;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

public final class SimulationRunner {

    public SimulationRunResult run(SimulationConfig config, long seed) {
        return run(config, seed, PairingMode.LOCAL);
    }

    public SimulationRunResult run(SimulationConfig config, long seed, PairingMode mode) {
        SimulationSession session = new SimulationSession(config, seed, mode);
        while (!session.isFinished()) {
            if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException("Simulation interrupted");
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

    TickGameResult playPairs(
            WorldGrid grid, SimulationConfig config, RandomGenerator random
    ) {
        return playPairs(grid, config, random, PairingMode.LOCAL);
    }

    TickGameResult playPairs(WorldGrid grid, SimulationConfig config, RandomGenerator random, PairingMode mode) {
        Map<Agent, Integer> payoffs = new LinkedHashMap<>();
        GameStatistics statistics = GameStatistics.EMPTY;
        long[] uses = new long[4];
        for (AgentPair pair : PairingPhase.formPairs(grid, random, mode)) {
            var match = RepeatedGame.playDetailed(
                    pair.first().genotype(), pair.second().genotype(), config.roundsPerMatch()
            );
            payoffs.put(pair.first(), match.payoff().firstAgentTotal());
            payoffs.put(pair.second(), match.payoff().secondAgentTotal());
            statistics = statistics.plus(match.statistics());
            uses[0] += match.afterCCUses();
            uses[1] += match.afterCDUses();
            uses[2] += match.afterDCUses();
            uses[3] += match.afterDDUses();
        }
        return new TickGameResult(payoffs, statistics, uses);
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
