package evolution.simulation;

import evolution.experiment.StudyMain;
import evolution.game.*;
import evolution.world.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import java.util.random.RandomGeneratorFactory;
import static org.junit.jupiter.api.Assertions.*;

class ResearchControlsTest {
    @TempDir Path temp;
    private java.util.random.RandomGenerator rng(long seed) {
        return RandomGeneratorFactory.of(SimulationSession.RANDOM_GENERATOR).create(seed);
    }
    private Genotype genotype(String s) {
        return new Genotype(Action.valueOf(s.substring(0,1)),Action.valueOf(s.substring(1,2)),
                Action.valueOf(s.substring(2,3)),Action.valueOf(s.substring(3,4)),Action.valueOf(s.substring(4,5)));
    }
    private SimulationConfig cfg(int k,int alpha,double pc,double mu) {
        return new SimulationConfig(8,16,5,k,1000,400,100,.05,alpha,pc,mu,3,4);
    }
    @Test void matchedModePreservesLocalPairCountInSameWorldAndNeverDuplicatesPlayers() {
        for(int n : new int[]{0,1,2,17,64}) for(long seed=1;seed<=20;seed++) {
            var grid=new WorldGrid(8);
            for(int i=0;i<n;i++) grid.placeAgent(new Agent(i+1,new CellPosition(i/8,i%8),1000,genotype("CCDCD")));
            var local=PairingPhase.formPairs(grid,rng(seed));
            var matched=PairingPhase.formPairs(grid,rng(seed),PairingMode.GLOBAL_MATCHED);
            assertEquals(local.size(),matched.size());
            var ids=new HashSet<Long>();
            for(var p:matched) { assertTrue(ids.add(p.first().agentId())); assertTrue(ids.add(p.second().agentId())); }
            assertEquals(matched,PairingPhase.formPairs(grid,rng(seed),PairingMode.GLOBAL_MATCHED));
        }
    }
    @Test void normalizedHomogeneousEncountersHaveExactSameEnergyForAllRoundCounts() {
        for(String g:List.of("CCCCC","DDDDD")) {
            Integer expected=null;
            for(int k:new int[]{1,5,10}) {
                int payoff=RepeatedGame.play(genotype(g),genotype(g),k).firstAgentTotal();
                int gain=(100/k)*payoff;
                if(expected==null) expected=gain;
                assertEquals(expected.intValue(),gain);
            }
        }
        var grid=new WorldGrid(8); var a=new Agent(1,new CellPosition(0,0),1000,genotype("CCCCC"));
        grid.placeAgent(a);
        EnergyPhase.applyEnergyAndRemoveDead(grid,Map.of(a,400),Map.of(a,30),cfg(10,10,.8,.01));
        assertEquals(1600,a.energyHundredths());
    }
    @Test void mutationExtremesAndEveryCrossoverCutAreValid() {
        var c=genotype("CCCCC"); var d=genotype("DDDDD");
        var cuts=new HashSet<Genotype>();
        for(int seed=0;seed<100;seed++) {
            assertEquals(c,Genetics.childGenotype(c,c,cfg(5,20,0,0),rng(seed)));
            assertEquals(d,Genetics.childGenotype(c,c,cfg(5,20,0,1),rng(seed)));
            cuts.add(Genetics.childGenotype(c,d,cfg(5,20,1,0),rng(seed)));
        }
        assertEquals(Set.of(genotype("CDDDD"),genotype("CCDDD"),genotype("CCCDD"),genotype("CCCCD")),cuts);
    }
    @Test void tournamentPicksHighestEnergyAndHandlesTiesAndSingleSurvivor() {
        var a=new Agent(1,new CellPosition(0,0),100,genotype("CCCCC"));
        var b=new Agent(2,new CellPosition(0,1),200,genotype("DDDDD"));
        for(int seed=0;seed<20;seed++) assertSame(b,Genetics.selectTournament(List.of(a,b),3,rng(seed)));
        assertSame(a,Genetics.selectTournament(List.of(a),3,rng(3)));
        a.changeEnergyBy(100); var winners=new HashSet<Agent>();
        for(int seed=0;seed<30;seed++) winners.add(Genetics.selectTournament(List.of(a,b),2,rng(seed)));
        assertEquals(Set.of(a,b),winners);
        assertThrows(IllegalArgumentException.class,()->Genetics.selectTournament(List.of(),3,rng(1)));
    }
    @Test void observationExportAndAllPairingModesPreserveTrajectory() throws Exception {
        for(var mode:PairingMode.values()) {
            var config=cfg(5,20,.8,.01); var s=new SimulationSession(config,654,mode);
            while(!s.isFinished()) {
                assertEquals(s.currentSnapshot(),s.currentSnapshot());
                s.advance();
            }
            var before=s.result();
            CsvResultsWriter.write(temp.resolve(mode.csvValue()),List.of(before));
            assertEquals(before,s.result());
            assertEquals(before,new SimulationRunner().run(config,654,mode));
            for(var m:before.generations()) {
                assertEquals(2*m.gameStatistics().matches()*4,m.afterCCUses()+m.afterCDUses()+m.afterDCUses()+m.afterDDUses());
                assertTrue(2*m.gameStatistics().matches()<=m.agentTicks());
            }
        }
    }
    @Test void genotypeGameAgainstAll32OpponentsMatchesIndependentReference() {
        var all=new ArrayList<String>();
        for(int i=0;i<32;i++) all.add(String.format("%5s",Integer.toBinaryString(i)).replace(' ','0').replace('0','C').replace('1','D'));
        for(String a:all) for(String b:all) for(int k:new int[]{1,2,5,10}) {
            char x=a.charAt(0),y=b.charAt(0); int pa=0,pb=0;
            for(int r=0;r<k;r++) {
                pa+=x=='C'?(y=='C'?3:0):(y=='C'?5:1);
                pb+=y=='C'?(x=='C'?3:0):(x=='C'?5:1);
                int ia=1+(x=='D'?2:0)+(y=='D'?1:0),ib=1+(y=='D'?2:0)+(x=='D'?1:0);
                x=a.charAt(ia);y=b.charAt(ib);
            }
            assertEquals(new MatchPayoff(pa,pb),RepeatedGame.play(genotype(a),genotype(b),k));
        }
    }
    @Test void nextGenerationUsesOnlyLivingParentsAndResetsEnergyAndIds() {
        var grid=new WorldGrid(8);
        var live=new Agent(1,new CellPosition(0,0),5000,genotype("CCCCC"));
        var dead=new Agent(2,new CellPosition(0,1),9000,genotype("DDDDD"));dead.die();
        grid.placeAgent(live);grid.placeAgent(dead);
        var config=cfg(5,20,0,0);
        long next=new SimulationRunner().createNextGeneration(grid,config,rng(11),10);
        assertEquals(26,next);assertEquals(16,grid.livingAgents().size());
        assertEquals(16,grid.livingAgents().stream().map(Agent::agentId).distinct().count());
        for(var a:grid.livingAgents()) {
            assertTrue(a.agentId()>=10 && a.agentId()<26);assertEquals(genotype("CCCCC"),a.genotype());
            assertEquals(config.initialEnergyHundredths(),a.energyHundredths());
        }
    }
    @Test void resourceRegrowthAndCollectionPrecedeDeathInFullImmobileWorld() {
        var config=new SimulationConfig(3,9,1,1,1,400,400,1,0,0,0,3,1);
        var session=new SimulationSession(config,4);var initial=session.currentSnapshot();session.advance();
        var after=session.currentSnapshot();assertEquals(9,after.agents().size());
        assertEquals(3600,after.metrics().resourceEnergyHundredths());
        for(int j=0;j<9;j++) {
            assertEquals(initial.agents().get(j).position(),after.agents().get(j).position());
            assertEquals(1,after.agents().get(j).energyHundredths());
        }
        assertTrue(after.resources().isEmpty());
    }
    @Test void externalProfilesAreStrictAndStudyControlsHaveFixedScale() throws Exception {
        Path p=temp.resolve("bad.properties"); Files.writeString(p,"sideLength=20\n");
        assertThrows(IllegalArgumentException.class,()->ConfigProfiles.load(p));
        var scenarios=StudyMain.scenarios(cfg(5,20,.8,.01));
        assertEquals(12,scenarios.size());
        for(var s:scenarios) if(s.id().startsWith("normalized") || s.id().equals("baseline"))
            assertEquals(100,s.config().roundsPerMatch()*s.config().gamePayoffWeightHundredths());
    }
}
