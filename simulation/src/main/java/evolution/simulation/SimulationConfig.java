package evolution.simulation;

public record SimulationConfig(
        int sideLength,
        int populationSize,
        int ticksPerGeneration,
        int roundsPerMatch,
        int initialEnergyHundredths,
        int resourceEnergyHundredths,
        int livingCostHundredths,
        double resourceSpawnProbability,
        int gamePayoffWeightHundredths,
        double crossoverProbability,
        double mutationProbability,
        int tournamentSize,
        int maximumGenerations
) {
    public SimulationConfig {
        if (sideLength < 3) {
            throw new IllegalArgumentException("Side length must be at least 3");
        }
        if (populationSize < 1 || populationSize > (long) sideLength * sideLength) {
            throw new IllegalArgumentException(
                    "Population size must be from 1 to the number of grid cells"
            );
        }
        if (ticksPerGeneration < 1) {
            throw new IllegalArgumentException("Ticks per generation must be at least 1");
        }
        if (roundsPerMatch < 1) {
            throw new IllegalArgumentException("Rounds per match must be at least 1");
        }
        if (initialEnergyHundredths <= 0) {
            throw new IllegalArgumentException("Initial energy must be positive");
        }
        if (resourceEnergyHundredths < 0 || livingCostHundredths < 0) {
            throw new IllegalArgumentException("Energy values must not be negative");
        }
        if (gamePayoffWeightHundredths < 0) {
            throw new IllegalArgumentException("Game payoff weight must not be negative");
        }
        requireProbability(resourceSpawnProbability, "Resource spawn probability");
        requireProbability(crossoverProbability, "Crossover probability");
        requireProbability(mutationProbability, "Mutation probability");

        if (tournamentSize < 1) {
            throw new IllegalArgumentException("Tournament size must be at least 1");
        }
        if (maximumGenerations < 1) {
            throw new IllegalArgumentException("Maximum generations must be at least 1");
        }
    }

    public static SimulationConfig baseline() {
        return new SimulationConfig(
                30,
                450,
                50,
                5,
                1_000,
                400,
                100,
                0.10,
                20,
                0.80,
                0.01,
                3,
                100
        );
    }

    private static void requireProbability(double value, String name) {
        if (Double.isNaN(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be between 0 and 1");
        }
    }
}
