package evolution.replay;

import evolution.simulation.*;
import java.nio.file.*;
import java.util.*;

/** Opt-in full frame export for a single deterministic run; research CSV stays lightweight. */
public final class RecordMain {
    private RecordMain() {}
    public static void main(String[] args) throws Exception {
        if(args.length==1 && args[0].equals("--help")) {
            System.out.println("--profile <file> --seed <long> --pairing LOCAL|GLOBAL|GLOBAL_MATCHED --output <new.eoc>");return;
        }
        var options=new HashMap<String,String>();
        for(int i=0;i<args.length;i+=2) {
            if(i+1>=args.length || !Set.of("--profile","--seed","--pairing","--output").contains(args[i])
                    || options.putIfAbsent(args[i],args[i+1])!=null) throw new IllegalArgumentException("Invalid/duplicate option. Use --help.");
        }
        if(!options.keySet().containsAll(Set.of("--profile","--seed","--output"))) throw new IllegalArgumentException("Required: --profile, --seed, --output");
        var config=ConfigProfiles.load(Path.of(options.get("--profile")));
        long seed=Long.parseLong(options.get("--seed"));var mode=PairingMode.valueOf(options.getOrDefault("--pairing","LOCAL"));
        var session=new SimulationSession(config,seed,mode);Path output=Path.of(options.get("--output")).toAbsolutePath();
        Files.createDirectories(output.getParent());
        try(var file=ReplayFile.create(output,config,seed,mode)) {
            file.append(session.currentSnapshot());
            while(!session.isFinished()) {session.advance();file.append(session.currentSnapshot());}
            System.out.println("Recorded "+file.size()+" frames ("+session.currentSnapshot().status()+"): "+output);
        }
    }
}
