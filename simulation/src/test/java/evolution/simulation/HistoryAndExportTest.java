package evolution.simulation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class HistoryAndExportTest {
    @TempDir Path temp;
    private SimulationConfig config() {
        return new SimulationConfig(8,16,8,5,1000,400,0,.1,20,.8,.01,3,3);
    }
    @Test void boundedHistoryRetainsReplayAndUsesElapsedTickForChartNavigation() {
        var h=new SimulationHistory(config(),12,5);
        for(int i=0;i<10;i++) h.stepForward();
        assertEquals(5,h.size());assertEquals(10,h.currentSnapshot().elapsedTicks());
        assertEquals(6,h.toStart().elapsedTicks());assertEquals(0,h.nearestIndex(6));assertEquals(4,h.nearestIndex(10));
        assertEquals(7,h.stepForward().elapsedTicks());assertEquals(10,h.toEnd().elapsedTicks());
        assertEquals(11,h.stepForward().elapsedTicks());
        h.newRun(config(),12,PairingMode.GLOBAL_MATCHED);assertEquals(1,h.size());assertEquals(0,h.currentSnapshot().elapsedTicks());
    }
    @Test void frameExportIsLegendedAndReadOnlyAndRefusesOverwrite() throws Exception {
        var s=new SimulationSession(config(),12);s.advance();var snapshot=s.currentSnapshot();
        Path png=temp.resolve("world.png");SnapshotExporter.write(png,snapshot,"baseline");
        var im=ImageIO.read(png.toFile());assertEquals(1000,im.getWidth());assertEquals(840,im.getHeight());
        String svg=Files.readString(Path.of(png+".svg"));assertTrue(svg.contains("seed=12"));assertTrue(svg.contains("CCDCD"));assertTrue(svg.contains("#efb72c"));
        assertEquals(snapshot,s.currentSnapshot());assertThrows(java.io.IOException.class,()->SnapshotExporter.write(png,snapshot,"baseline"));
    }
}
