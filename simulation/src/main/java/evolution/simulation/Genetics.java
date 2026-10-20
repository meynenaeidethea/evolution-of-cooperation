package evolution.simulation;

import evolution.game.Action;
import evolution.game.Genotype;
import evolution.world.Agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

public final class Genetics {

    private Genetics() {
    }

    public static Genotype randomGenotype(RandomGenerator random) {
        Objects.requireNonNull(random, "Random generator must be defined");
        return new Genotype(action(random), action(random), action(random), action(random), action(random));
    }

    public static Agent selectTournament(
            List<Agent> survivors, int tournamentSize, RandomGenerator random
    ) {
        Objects.requireNonNull(survivors, "Survivors must be defined");
        Objects.requireNonNull(random, "Random generator must be defined");
        if (survivors.isEmpty()) {
            throw new IllegalArgumentException("At least one survivor is required");
        }

        if (tournamentSize < 1) throw new IllegalArgumentException("Tournament size must be positive");
        int count = Math.min(tournamentSize, survivors.size());
        // Partial Fisher-Yates through a sparse index map: O(tournamentSize), without replacement.
        java.util.Map<Integer, Integer> swaps = new java.util.HashMap<>();
        List<Agent> candidates = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int chosen = i + random.nextInt(survivors.size() - i);
            int selected = swaps.getOrDefault(chosen, chosen);
            swaps.put(chosen, swaps.getOrDefault(i, i));
            candidates.add(survivors.get(selected));
        }
        int highestEnergy = candidates.getFirst().energyHundredths();
        for (int index = 1; index < count; index++) {
            highestEnergy = Math.max(highestEnergy, candidates.get(index).energyHundredths());
        }
        int winningEnergy = highestEnergy;

        List<Agent> winners = candidates.subList(0, count).stream()
                .filter(agent -> agent.energyHundredths() == winningEnergy)
                .toList();
        return winners.get(random.nextInt(winners.size()));
    }

    public static Genotype childGenotype(
            Genotype firstParent,
            Genotype secondParent,
            SimulationConfig config,
            RandomGenerator random
    ) {
        Objects.requireNonNull(firstParent, "First parent must be defined");
        Objects.requireNonNull(secondParent, "Second parent must be defined");
        Objects.requireNonNull(config, "Configuration must be defined");
        Objects.requireNonNull(random, "Random generator must be defined");

        Action[] first = genes(firstParent);
        Action[] second = genes(secondParent);
        Action[] child = new Action[5];

        if (random.nextDouble() < config.crossoverProbability()) {
            int cut = 1 + random.nextInt(4);
            for (int index = 0; index < 5; index++) {
                child[index] = index < cut ? first[index] : second[index];
            }
        } else {
            Action[] chosen = random.nextBoolean() ? first : second;
            System.arraycopy(chosen, 0, child, 0, child.length);
        }

        for (int index = 0; index < child.length; index++) {
            if (random.nextDouble() < config.mutationProbability()) {
                child[index] = child[index] == Action.C ? Action.D : Action.C;
            }
        }
        return new Genotype(child[0], child[1], child[2], child[3], child[4]);
    }

    private static Action action(RandomGenerator random) {
        return random.nextBoolean() ? Action.C : Action.D;
    }

    private static Action[] genes(Genotype genotype) {
        return new Action[] {
                genotype.firstMove(), genotype.afterCC(), genotype.afterCD(),
                genotype.afterDC(), genotype.afterDD()
        };
    }

    private static <T> void shuffle(List<T> values, RandomGenerator random) {
        for (int index = values.size() - 1; index > 0; index--) {
            int other = random.nextInt(index + 1);
            T value = values.get(index);
            values.set(index, values.get(other));
            values.set(other, value);
        }
    }
}
