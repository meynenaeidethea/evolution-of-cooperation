import evolution.simulation.*;
import java.nio.file.Path;
/** A retained-heap diagnostic, not a precision allocation benchmark. Run with -Xmx512m. */
class HistoryMemoryCheck {
    public static void main(String[] args) throws Exception {
        var h=new SimulationHistory(ConfigProfiles.load(Path.of("profiles/user.properties")),1);
        for(int i=1;i<=1200 && h.canStepForward();i++) {
            h.stepForward();
            if(i%300==0) {
                System.gc();Thread.sleep(150);
                long heap=Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory();
                System.out.println("tick="+i+" snapshots="+h.size()+" retained_heap_bytes="+heap);
                if(h.size()>300)throw new IllegalStateException("History limit");
            }
        }
    }
}
