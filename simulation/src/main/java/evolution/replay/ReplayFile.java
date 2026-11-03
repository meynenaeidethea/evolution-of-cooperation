package evolution.replay;

import evolution.simulation.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Append-only frame journal. Each independent compressed frame has a length and CRC.
 * A truncated final write is recoverable; corrupted complete frames are rejected. */
public final class ReplayFile implements AutoCloseable {
    private static final String MAGIC="EOC-REPLAY";
    private static final int VERSION=1, MAX_FRAME=64*1024*1024;
    public record Metadata(SimulationConfig config,long seed,PairingMode pairing,String model,String javaVersion) {}
    private final RandomAccessFile file;
    private final Path path;
    private final Metadata metadata;
    private final boolean writable;
    private final List<Long> offsets=new ArrayList<>();
    private final Map<Integer,SimulationSnapshot> cache=new LinkedHashMap<>(32,.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Integer,SimulationSnapshot> e) {return size()>300;}
    };
    private boolean truncatedTail;

    private ReplayFile(Path path,boolean writable,Metadata metadata) throws IOException {
        this.path=path.toAbsolutePath().normalize(); this.writable=writable;
        file=new RandomAccessFile(this.path.toFile(),writable?"rw":"r");
        try {
            if(writable) {
                this.metadata=metadata;
                file.writeUTF(MAGIC);file.writeInt(VERSION);file.writeUTF(ConfigProfiles.format(metadata.config()));
                file.writeLong(metadata.seed());file.writeUTF(metadata.pairing().name());
                file.writeUTF(metadata.model());file.writeUTF(metadata.javaVersion());file.getFD().sync();
            } else {
                if(!MAGIC.equals(file.readUTF()) || file.readInt()!=VERSION) throw new IOException("Unsupported replay format/version");
                this.metadata=new Metadata(ConfigProfiles.read(new StringReader(file.readUTF())),file.readLong(),
                        PairingMode.valueOf(file.readUTF()),file.readUTF(),file.readUTF());
                scan();
                if(offsets.isEmpty()) throw new IOException("Replay contains no complete frames");
            }
        } catch(Exception e) {file.close();throw new IOException("Cannot open replay: "+e.getMessage(),e);}
    }
    public static ReplayFile create(Path path,SimulationConfig config,long seed,PairingMode mode) throws IOException {
        Files.createFile(path); // Refuse overwrite, including another process's recording.
        return new ReplayFile(path,true,new Metadata(config,seed,mode,SimulationSession.MODEL_VERSION,Runtime.version().toString()));
    }
    public static ReplayFile open(Path path) throws IOException {return new ReplayFile(path,false,null);}
    public Metadata metadata() {return metadata;}
    public Path path() {return path;}
    public int size() {return offsets.size();}
    public int cachedFrames() {return cache.size();}
    public boolean truncatedTail() {return truncatedTail;}

    private void scan() throws IOException {
        while(file.getFilePointer()<file.length()) {
            long offset=file.getFilePointer(), remaining=file.length()-offset;
            if(remaining<4) {truncatedTail=true;break;}
            int length=file.readInt();
            if(length<1 || length>MAX_FRAME) throw new IOException("Invalid replay frame length");
            if(remaining<8L+length) {truncatedTail=true;break;}
            byte[] payload=new byte[length];file.readFully(payload);
            checkCrc(payload,file.readInt());
            var snapshot=decode(payload);validate(snapshot,offsets.size());
            offsets.add(offset);
        }
    }
    public void append(SimulationSnapshot snapshot) throws IOException {
        if(!writable) throw new IOException("Replay is read-only");
        validate(snapshot,offsets.size());
        var raw=new ByteArrayOutputStream();
        try(var out=new DataOutputStream(raw)) {SnapshotCodec.write(out,snapshot);}
        if(raw.size()>MAX_FRAME) throw new IOException("Snapshot exceeds 64 MiB");
        var bytes=new ByteArrayOutputStream();
        try(var out=new DeflaterOutputStream(bytes)) {raw.writeTo(out);}
        byte[] payload=bytes.toByteArray();
        if(payload.length>MAX_FRAME) throw new IOException("Replay frame exceeds 64 MiB");
        long offset=file.length();file.seek(offset);
        try {
            file.writeInt(payload.length);file.write(payload);file.writeInt(crc(payload));
        } catch(IOException e) {file.setLength(offset);throw e;}
        offsets.add(offset); cache.put(offsets.size()-1,snapshot);
    }
    public SimulationSnapshot get(int index) throws IOException {
        if(index<0 || index>=size()) throw new IndexOutOfBoundsException(index);
        var saved=cache.get(index);if(saved!=null) return saved;
        file.seek(offsets.get(index));int length=file.readInt();
        if(length<1 || length>MAX_FRAME) throw new IOException("Invalid replay frame length");
        byte[] payload=new byte[length];file.readFully(payload);checkCrc(payload,file.readInt());
        var snapshot=decode(payload);validate(snapshot,index);cache.put(index,snapshot);return snapshot;
    }
    private void validate(SimulationSnapshot s,int index) throws IOException {
        if(s.elapsedTicks()!=index || s.seed()!=metadata.seed() || s.sideLength()!=metadata.config().sideLength())
            throw new IOException("Replay frame identity/order mismatch");
    }
    private static SimulationSnapshot decode(byte[] bytes) throws IOException {
        try(var inflater=new InflaterInputStream(new ByteArrayInputStream(bytes))) {
            // Bound inflated input before the codec allocates any lists.
            byte[] raw=inflater.readNBytes(MAX_FRAME+1);
            if(raw.length>MAX_FRAME) throw new IOException("Inflated frame exceeds 64 MiB");
            try(var in=new DataInputStream(new ByteArrayInputStream(raw))) {
                var s=SnapshotCodec.read(in);if(in.read()!=-1) throw new IOException("Trailing snapshot data");return s;
            }
        }
    }
    private static int crc(byte[] bytes) {var crc=new CRC32();crc.update(bytes);return (int)crc.getValue();}
    private static void checkCrc(byte[] bytes,int expected) throws IOException {
        if(crc(bytes)!=expected) throw new IOException("Replay frame checksum mismatch");
    }
    public void copyTo(Path destination) throws IOException {
        if(writable) file.getFD().sync();
        Files.copy(path,destination); // A copy never overwrites a previous recording.
    }
    @Override public void close() throws IOException {try {if(writable) file.getFD().sync();} finally {file.close();}}
}
