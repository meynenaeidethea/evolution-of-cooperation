package evolution.simulation;

import evolution.game.Action;
import evolution.game.Genotype;
import evolution.world.Agent;
import evolution.world.CellPosition;
import evolution.world.WorldGrid;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovementPhaseTest {

    private static final Genotype ALWAYS_COOPERATE =
            new Genotype(Action.C, Action.C, Action.C, Action.C, Action.C);

    @Test
    void choosesOneFreeSnapshotTargetForEachAgentWithoutMovingThem() {
        WorldGrid grid = new WorldGrid(3);
        Agent firstAgent = new Agent(
                1,
                new CellPosition(0, 0),
                1_000,
                ALWAYS_COOPERATE
        );
        Agent secondAgent = new Agent(
                2,
                new CellPosition(0, 1),
                1_000,
                ALWAYS_COOPERATE
        );
        grid.placeAgent(firstAgent);
        grid.placeAgent(secondAgent);

        List<MovementIntent> intents = MovementPhase.chooseIntents(
                grid,
                randomWithSeed(1)
        );

        assertEquals(2, intents.size());
        assertEquals(new CellPosition(0, 0), firstAgent.position());
        assertEquals(new CellPosition(0, 1), secondAgent.position());

        MovementIntent firstIntent = intentFor(intents, firstAgent);
        MovementIntent secondIntent = intentFor(intents, secondAgent);

        assertNotEquals(secondAgent.position(), firstIntent.target());
        assertNotEquals(firstAgent.position(), secondIntent.target());
    }

    @Test
    void resolvesAContestedTargetWithExactlyOneWinner() {
        WorldGrid grid = new WorldGrid(3);
        Agent firstAgent = new Agent(
                1,
                new CellPosition(0, 0),
                1_000,
                ALWAYS_COOPERATE
        );
        Agent secondAgent = new Agent(
                2,
                new CellPosition(0, 2),
                1_000,
                ALWAYS_COOPERATE
        );
        CellPosition contestedTarget = new CellPosition(0, 1);

        grid.placeAgent(firstAgent);
        grid.placeAgent(secondAgent);

        List<MovementIntent> winners = MovementPhase.resolveAndApply(
                grid,
                List.of(
                        new MovementIntent(firstAgent, contestedTarget),
                        new MovementIntent(secondAgent, contestedTarget)
                ),
                randomWithSeed(1)
        );

        assertEquals(1, winners.size());

        Agent winner = grid.agentAt(contestedTarget).orElseThrow();
        Agent loser = winner == firstAgent ? secondAgent : firstAgent;

        assertEquals(contestedTarget, winner.position());
        assertEquals(
                loser == firstAgent
                        ? new CellPosition(0, 0)
                        : new CellPosition(0, 2),
                loser.position()
        );
    }

    @Test
    void rejectsExchangeOfCellsOccupiedAtPhaseStart() {
        WorldGrid grid = new WorldGrid(3);
        Agent firstAgent = new Agent(
                1,
                new CellPosition(0, 0),
                1_000,
                ALWAYS_COOPERATE
        );
        Agent secondAgent = new Agent(
                2,
                new CellPosition(0, 1),
                1_000,
                ALWAYS_COOPERATE
        );
        grid.placeAgent(firstAgent);
        grid.placeAgent(secondAgent);

        assertThrows(
                IllegalArgumentException.class,
                () -> MovementPhase.resolveAndApply(
                        grid,
                        List.of(
                                new MovementIntent(
                                        firstAgent,
                                        new CellPosition(0, 1)
                                ),
                                new MovementIntent(
                                        secondAgent,
                                        new CellPosition(0, 0)
                                )
                        ),
                        randomWithSeed(1)
                )
        );

        assertEquals(new CellPosition(0, 0), firstAgent.position());
        assertEquals(new CellPosition(0, 1), secondAgent.position());
        assertTrue(grid.agentAt(new CellPosition(0, 0)).isPresent());
        assertFalse(grid.agentAt(new CellPosition(0, 2)).isPresent());
    }

    private MovementIntent intentFor(
            List<MovementIntent> intents,
            Agent agent
    ) {
        return intents.stream()
                .filter(intent -> intent.agent() == agent)
                .findFirst()
                .orElseThrow();
    }

    private RandomGenerator randomWithSeed(long seed) {
        return RandomGeneratorFactory
                .of("L64X128MixRandom")
                .create(seed);
    }
}