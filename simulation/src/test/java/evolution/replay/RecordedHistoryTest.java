package evolution.replay;

import evolution.simulation.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class RecordedHistoryTest {
    @TempDir Path temp;
    private final SimulationConfig config=new SimulationConfig(8,16,170,5,1000,400,0,.1,20,.8,.01,3,2);

    @Test void fullHistorySurvivesCacheEvictionAndReopeningAndReplayDoesNotChangeFuture() throws Exception {
        var control=new SimulationSession(config,12,PairingMode.GLOBAL_MATCHED);
        Path copy=temp.resolve("saved.eoc");
        try(var h=RecordedHistory.create(temp,config,12,PairingMode.GLOBAL_MATCHED)) {
            var initial=h.view().snapshot();
            for(int i=0;i<320;i++) {h.stepForward();control.advance();}
            assertEquals(control.currentSnapshot(),h.view().snapshot());
            assertEquals(321,h.view().size());assertEquals(300,h.view().window().size());
            h.toStart();assertEquals(initial,h.view().snapshot());
            h.seek(170);assertEquals(1,h.view().snapshot().generation());
            h.stepForward();assertEquals(2,h.view().snapshot().generation());
            h.toEnd();h.stepForward();control.advance();
            assertEquals(control.currentSnapshot(),h.view().snapshot());
            h.copyTo(copy);assertThrows(FileAlreadyExistsException.class,()->h.copyTo(copy));
        }
        try(var h=RecordedHistory.open(copy)) {
            assertEquals(config,h.view().metadata().config());
            assertEquals(PairingMode.GLOBAL_MATCHED,h.view().metadata().pairing());
            assertEquals(0,h.view().cursor());assertTrue(h.view().readOnly());
            h.toEnd();assertEquals(control.currentSnapshot(),h.view().snapshot());
            assertFalse(h.canStepForward());
        }
        try(var f=ReplayFile.open(copy)) {
            for(int i=0;i<f.size();i++) f.get(i);
            assertTrue(f.cachedFrames()<=300);
        }
    }

    @Test void truncatedTailRecoversOnlyCompleteFramesAndCorruptionIsRejected() throws Exception {
        Path path;
        try(var h=RecordedHistory.create(temp,config,12,PairingMode.LOCAL)) {
            for(int i=0;i<3;i++) h.stepForward();path=h.view().path();
        }
        Path truncated=temp.resolve("truncated.eoc");Files.copy(path,truncated);
        try(var f=new RandomAccessFile(truncated.toFile(),"rw")) {f.setLength(f.length()-3);}
        try(var h=RecordedHistory.open(truncated)) {
            assertTrue(h.view().recovered());assertEquals(3,h.view().size());
            h.toEnd();assertEquals(2,h.view().snapshot().elapsedTicks());
        }
        try(var f=new RandomAccessFile(path.toFile(),"rw")) {
            f.seek(f.length()-5);int b=f.read();f.seek(f.length()-5);f.write(b^1);
        }
        assertThrows(IOException.class,()->RecordedHistory.open(path));
    }

    @Test void extinctAndInitialMissingValuesRoundTripAndFilesAreNeverOverwritten() throws Exception {
        var dying=new SimulationConfig(3,1,3,1,100,0,100,0,0,0,0,1,2);
        Path path;
        try(var h=RecordedHistory.create(temp,dying,1,PairingMode.LOCAL)) {
            path=h.view().path();h.stepForward();assertEquals("extinct",h.view().snapshot().status());
            assertFalse(h.canStepForward());
        }
        assertThrows(FileAlreadyExistsException.class,()->ReplayFile.create(path,dying,1,PairingMode.LOCAL));
        try(var h=RecordedHistory.open(path)) {
            assertTrue(Double.isNaN(h.view().snapshot().metrics().lastTick().gameStatistics().cooperationRate()));
            h.toEnd();assertTrue(Double.isNaN(h.view().snapshot().metrics().averageEnergyHundredths()));
            assertEquals(1,h.view().snapshot().metrics().lastTick().deaths());
            assertEquals("extinct",h.view().snapshot().status());
        }
    }
}
