import evolution.experiment.StudyScenarios;
import evolution.game.Action;
import evolution.game.Genotype;
import evolution.simulation.ConfigProfiles;
import evolution.simulation.GenerationMetrics;
import evolution.simulation.SimulationSession;
import evolution.world.Agent;
import evolution.world.WorldGrid;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class InvasionStudy {
    private static final Genotype COOPERATOR =
            new Genotype(Action.C, Action.C, Action.D, Action.D, Action.D);
    private static final Genotype DEFECTOR =
            new Genotype(Action.D, Action.D, Action.D, Action.D, Action.D);
    private static final List<String> GENOTYPES = genotypeKeys();

    public static void main(String[] args) throws ReflectiveOperationException, java.io.IOException {
        var profile = ConfigProfiles.load(Path.of(args[0]));
        var scenario = StudyScenarios.create(profile).stream()
                .filter(s -> s.id().equals("rounds-10"))
                .findFirst().orElseThrow();

        // The public session API exposes detached snapshots only. Keep this
        // experiment-only access confined to setup, before the first tick.
        Field gridField = SimulationSession.class.getDeclaredField("grid");
        gridField.setAccessible(true);

        System.out.println("condition,seed,generation,ticks,living_agents,extinct,"
                + "cooperative_actions,played_actions,C,n_" + String.join(",n_", GENOTYPES));
        int repetitions = Integer.parseInt(args[1]);
        for (long seed = 41001; seed < 41001 + repetitions; seed++) {
            for (boolean defectorInvades : new boolean[]{true, false}) {
                var session = new SimulationSession(scenario.config(), seed, scenario.pairingMode());
                var grid = (WorldGrid) gridField.get(session);
                var agents = new ArrayList<>(grid.livingAgents());
                int minority = agents.size() / 20;
                if (minority == 0 || minority * 20 != agents.size()) {
                    throw new IllegalStateException("Cannot assign exactly 5% of the population");
                }
                // Use the same minority sites in both runs, and preserve the simulation's PRNG state.
                Collections.shuffle(agents, new Random(seed ^ 0x32f1db6cL));
                for (int i = 0; i < agents.size(); i++) {
                    var agent = agents.get(i);
                    var genotype = (i < minority) == defectorInvades ? DEFECTOR : COOPERATOR;
                    grid.removeAgent(agent);
                    grid.placeAgent(new Agent(agent.agentId(), agent.position(),
                            agent.energyHundredths(), genotype));
                }

                String condition = defectorInvades ? "defectors" : "cooperators";
                writeRow(condition, seed, 0, session.currentSnapshot().metrics().generationMetrics());
                while (!session.isFinished()) {
                    session.advance();
                }
                for (var metrics : session.result().generations()) {
                    writeRow(condition, seed, metrics.generation(), metrics);
                }
            }
        }
    }

    private static List<String> genotypeKeys() {
        var keys = new ArrayList<String>();
        for (int bits = 0; bits < 32; bits++) {
            var key = new StringBuilder();
            for (int locus = 4; locus >= 0; locus--) {
                key.append((bits & (1 << locus)) == 0 ? 'C' : 'D');
            }
            keys.add(key.toString());
        }
        return List.copyOf(keys);
    }

    private static void writeRow(String condition, long seed, int generation, GenerationMetrics m) {
        var game = m.gameStatistics();
        var row = new StringBuilder(condition).append(',').append(seed).append(',').append(generation)
                .append(',').append(m.ticks()).append(',').append(m.livingAgents()).append(',')
                .append(m.extinct()).append(',').append(game.cooperativeActions())
                .append(',').append(game.actions()).append(',');
        if (game.actions() > 0) {
            row.append(game.cooperationRate());
        }
        for (var genotype : GENOTYPES) {
            row.append(',').append(m.genotypeCounts().getOrDefault(genotype, 0));
        }
        System.out.println(row);
    }
}
