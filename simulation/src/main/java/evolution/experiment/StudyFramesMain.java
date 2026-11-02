package evolution.experiment;
import evolution.simulation.*;
import java.nio.file.*;
import java.util.*;

/** Fixed, pre-selected seed illustrates the same model; stores at most one snapshot at a time. */
public final class StudyFramesMain {
    public static void main(String[] args) throws Exception {
        Path profile=Path.of(args.length>0?args[0]:"profiles/research.properties");
        Path out=Path.of(args.length>1?args[1]:"results/frames-20261020");Files.createDirectory(out);
        for(var sc:StudyMain.scenarios(ConfigProfiles.load(profile))) {
            if(!Set.of("baseline","global","global-matched").contains(sc.id()))continue;
            var session=new SimulationSession(sc.config(),10001,sc.pairingMode());
            while(!session.isFinished()) {
                session.advance();
                // Avoid copying the world each tick: generation boundary follows elapsed ticks here unless extinct.
                if(session.isFinished() || sessionElapsedBoundary(session,sc)) {
                    var s=session.currentSnapshot();
                    if(Set.of(1,30,60).contains(s.generation()) || s.status().equals("extinct"))
                        SnapshotExporter.write(out.resolve(sc.id()+"-g"+s.generation()+".png"),s,sc.id());
                }
            }
        }
    }
    private static boolean sessionElapsedBoundary(SimulationSession s,ExperimentScenario sc) {
        return s.elapsedTicks()%sc.config().ticksPerGeneration()==0;
    }
}
