package evolution.simulation;

import evolution.game.GameStatistics;
import evolution.world.Agent;

import java.util.Map;

record TickGameResult(Map<Agent, Integer> payoffs, GameStatistics statistics, long[] reactionUses) {
    TickGameResult {
        payoffs = Map.copyOf(payoffs);
        reactionUses = reactionUses.clone();
    }
}
