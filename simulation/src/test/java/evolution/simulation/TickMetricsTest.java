package evolution.simulation;

import evolution.game.GameStatistics;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TickMetricsTest {
    @Test void tickFlowsSumToGenerationAndResetAtBoundaryWithoutCountingOldGeneration() {
        var c=new SimulationConfig(8,16,12,5,1000,400,100,.1,20,.8,.01,3,3);
        var session=new SimulationSession(c,12);
        assertEquals(SimulationSnapshot.TickMetrics.EMPTY,session.currentSnapshot().metrics().lastTick());
        long resources=0,payoff=0;var games=GameStatistics.EMPTY;int generation=1;
        while(!session.isFinished()) {
            var before=session.currentSnapshot();session.advance();var s=session.currentSnapshot();
            if(s.generation()!=generation) {resources=0;payoff=0;games=GameStatistics.EMPTY;generation=s.generation();}
            var t=s.metrics().lastTick();resources+=t.resourceEnergyHundredths();payoff+=t.gamePayoff();games=games.plus(t.gameStatistics());
            assertEquals(resources,s.metrics().generationMetrics().resourceEnergyHundredths());
            assertEquals(payoff,s.metrics().generationMetrics().gamePayoff());
            assertEquals(games,s.metrics().generationMetrics().gameStatistics());
            int startPopulation=before.generation()==s.generation()?before.agents().size():c.populationSize();
            assertEquals(startPopulation-s.agents().size(),t.deaths());
        }
    }
}
