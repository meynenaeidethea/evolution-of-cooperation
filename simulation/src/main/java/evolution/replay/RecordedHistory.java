package evolution.replay;

import evolution.simulation.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Owned by one worker. Every computed frame is durable; only a bounded window is projected to UI. */
public final class RecordedHistory implements AutoCloseable {
    public record View(ReplayFile.Metadata metadata,Path path,SimulationSnapshot snapshot,
                       List<SimulationSnapshot> window,int cursor,int size,boolean canStepForward,
                       boolean readOnly,boolean recovered,String failure) {
        public View {window=List.copyOf(window);}
    }
    private final ReplayFile recording;
    private final SimulationSession session;
    private int cursor;
    private IOException failure;

    private RecordedHistory(ReplayFile file,SimulationSession session) {this.recording=file;this.session=session;}
    public static RecordedHistory create(Path directory,SimulationConfig c,long seed,PairingMode mode) throws IOException {
        Files.createDirectories(directory);
        Path path=directory.resolve("run-"+java.time.LocalDateTime.now().toString().replace(':','-')+"-"+UUID.randomUUID()+".eoc");
        var session=new SimulationSession(c,seed,mode);
        var file=ReplayFile.create(path,c,seed,mode);
        try {file.append(session.currentSnapshot());return new RecordedHistory(file,session);}
        catch(Exception e) {file.close();throw e;}
    }
    public static RecordedHistory open(Path path) throws IOException {return new RecordedHistory(ReplayFile.open(path),null);}
    public boolean canStepForward() {return cursor<recording.size()-1 || session!=null && !session.isFinished() && failure==null;}
    public void stepForward() throws IOException {
        if(cursor<recording.size()-1) {cursor++;return;}
        if(!canStepForward()) return;
        try {session.advance();recording.append(session.currentSnapshot());cursor++;}
        catch(Exception e) {failure=new IOException("Расчёт/запись остановлены: "+e.getMessage(),e);throw failure;}
    }
    public void seek(int index) {
        if(index<0 || index>=recording.size()) throw new IndexOutOfBoundsException(index);
        cursor=index;
    }
    public void back() {seek(Math.max(0,cursor-1));}
    public void toStart() {seek(0);}
    public void toEnd() {seek(recording.size()-1);}
    public View view() throws IOException {
        var window=new ArrayList<SimulationSnapshot>();
        for(int i=Math.max(0,cursor-299);i<=cursor;i++) window.add(recording.get(i));
        return new View(recording.metadata(),recording.path(),recording.get(cursor),window,cursor,recording.size(),
                canStepForward(),session==null,recording.truncatedTail(),failure==null?null:failure.getMessage());
    }
    public void copyTo(Path path) throws IOException {recording.copyTo(path);}
    @Override public void close() throws IOException {recording.close();}
}
