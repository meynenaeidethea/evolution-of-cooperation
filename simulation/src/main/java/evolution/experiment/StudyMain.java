package evolution.experiment;

import evolution.simulation.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/** Frozen study runner; one session/PRNG per job, per-run checkpoints, deterministic table order. */
public final class StudyMain {
    public static List<ExperimentScenario> scenarios(SimulationConfig c) {
        return List.of(new ExperimentScenario("baseline", c),
                new ExperimentScenario("rounds-1", change(c,1,20,c.mutationProbability(),c.resourceSpawnProbability())),
                new ExperimentScenario("rounds-10", change(c,10,20,c.mutationProbability(),c.resourceSpawnProbability())),
                new ExperimentScenario("normalized-1", change(c,1,100,c.mutationProbability(),c.resourceSpawnProbability())),
                new ExperimentScenario("normalized-10", change(c,10,10,c.mutationProbability(),c.resourceSpawnProbability())),
                new ExperimentScenario("game-zero", change(c,5,0,c.mutationProbability(),c.resourceSpawnProbability())),
                new ExperimentScenario("mutation-zero", change(c,5,20,0,c.resourceSpawnProbability())),
                new ExperimentScenario("mutation-high", change(c,5,20,.05,c.resourceSpawnProbability())),
                new ExperimentScenario("resource-poor", change(c,5,20,c.mutationProbability(),.02)),
                new ExperimentScenario("resource-rich", change(c,5,20,c.mutationProbability(),.25)),
                new ExperimentScenario("global", c, PairingMode.GLOBAL),
                new ExperimentScenario("global-matched", c, PairingMode.GLOBAL_MATCHED));
    }
    private static SimulationConfig change(SimulationConfig c,int k,int alpha,double mu,double q) {
        return new SimulationConfig(c.sideLength(),c.populationSize(),c.ticksPerGeneration(),k,
                c.initialEnergyHundredths(),c.resourceEnergyHundredths(),c.livingCostHundredths(),q,
                alpha,c.crossoverProbability(),mu,c.tournamentSize(),c.maximumGenerations());
    }
    public static void main(String[] args) throws Exception {
        var opts = new LinkedHashMap<String,String>();
        for (int i=0;i<args.length;i+=2) {
            if (i+1==args.length || !Set.of("--profile","--output","--repetitions","--seed-start","--threads","--scenarios").contains(args[i])
                    || opts.putIfAbsent(args[i],args[i+1])!=null) throw new IllegalArgumentException("Expected unique option/value pairs");
        }
        Path profile=Path.of(opts.getOrDefault("--profile","profiles/research.properties"));
        var c=ConfigProfiles.load(profile);
        int n=Integer.parseInt(opts.getOrDefault("--repetitions","40"));
        long start=Long.parseLong(opts.getOrDefault("--seed-start","10001"));
        int threads=Integer.parseInt(opts.getOrDefault("--threads","2"));
        if(n<1 || threads<1 || threads>32) throw new IllegalArgumentException("Positive repetitions; threads 1..32");
        var scenarios=scenarios(c);
        if(opts.containsKey("--scenarios")) {
            var ids=new HashSet<>(Arrays.asList(opts.get("--scenarios").split(",")));
            scenarios=scenarios.stream().filter(s->ids.remove(s.id())).toList();
            if(!ids.isEmpty() || scenarios.isEmpty()) throw new IllegalArgumentException("Unknown/empty scenarios: "+ids);
        }
        Path out=Path.of(opts.getOrDefault("--output","results/study-"+System.currentTimeMillis()));
        Files.createDirectories(out.toAbsolutePath().getParent());
        Files.createDirectory(out);
        Files.copy(profile,out.resolve("profile.properties"));
        Files.createDirectories(out.resolve("checkpoints"));
        String revision=git("rev-parse","HEAD"), status=git("status","--porcelain");
        String dirty=status.equals("unavailable") ? "unknown" : Boolean.toString(!status.isBlank());
        Files.writeString(out.resolve("source.patch"),git("diff","--binary"));
        Files.writeString(out.resolve("manifest.txt"),"model="+SimulationSession.MODEL_VERSION+"\nschema=2\nrevision="+revision.strip()
                +"\ndirty="+dirty+"\njava="+Runtime.version()+"\nprng="+SimulationSession.RANDOM_GENERATOR
                +"\nseed_start="+start+"\nrepetitions="+n+"\nthreads="+threads+"\nargs="+Arrays.toString(args)
                +"\nscenarios="+scenarios+"\n");
        // Archive the exact working-tree implementation, including new untracked source, not just a diff.
        Path source=out.resolve("source");
        for(String dir:List.of("simulation/src","profiles","tools","docs")) {
            try(var paths=Files.walk(Path.of(dir))) {
                for(Path p:paths.filter(Files::isRegularFile).sorted().toList()) {
                    Path dest=source.resolve(p); Files.createDirectories(dest.getParent()); Files.copy(p,dest);
                }
            }
        }
        for(String file:List.of("simulation/build.gradle.kts","settings.gradle.kts","gradle.properties","gradlew","gradlew.bat")) {
            Path dest=source.resolve(file); Files.createDirectories(dest.getParent()); Files.copy(Path.of(file),dest);
        }
        try(var paths=Files.walk(Path.of("gradle"))) {
            for(Path p:paths.filter(Files::isRegularFile).toList()) {
                Path dest=source.resolve(p); Files.createDirectories(dest.getParent()); Files.copy(p,dest);
            }
        }
        var futures=new ArrayList<Future<ExperimentRun>>();
        var started=System.nanoTime();
        try(var pool=Executors.newFixedThreadPool(threads)) {
            for(var scenario:scenarios) for(int rep=1;rep<=n;rep++) {
                final int repetition=rep; final long seed=Math.addExact(start,rep-1L);
                futures.add(pool.submit(()->{
                    long t=System.nanoTime();
                    var result=new SimulationRunner().run(scenario.config(),seed,scenario.pairingMode());
                    var run=new ExperimentRun(scenario.id(),repetition,result);
                    Path checkpoint=out.resolve("checkpoints").resolve(scenario.id()+"-"+repetition);
                    Files.createDirectory(checkpoint);
                    CsvResultsWriter.writeExperiments(checkpoint,List.of(run));
                    Files.writeString(checkpoint.resolve("execution.properties"),"duration_seconds="+(System.nanoTime()-t)/1e9
                            +"\nelapsed_ticks="+result.generations().stream().mapToInt(GenerationMetrics::ticks).sum()
                            +"\nrevision="+revision.strip()+"\ndirty="+dirty+"\nschema=2\n");
                    return run;
                }));
            }
            var runs=new ArrayList<ExperimentRun>();
            for(var future:futures) {
                var r=future.get(); runs.add(r);
                System.out.println("["+runs.size()+"/"+futures.size()+"] "+r.scenarioId()+" seed="+r.result().seed()+" "+r.result().status());
            }
            CsvResultsWriter.writeExperiments(out,runs);
        }
        Files.writeString(out.resolve("completed.txt"),"wall_seconds="+(System.nanoTime()-started)/1e9+"\n");
        checksums(out);
    }
    private static String git(String... args) throws Exception {
        var command=new ArrayList<String>(); command.add("git"); command.addAll(List.of(args));
        var p=new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        String result=new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return p.waitFor()==0 ? result : "unavailable";
    }
    public static void checksums(Path out) throws Exception {
        var lines=new ArrayList<String>();
        try(var paths=Files.walk(out)) {
            for(Path p:paths.filter(Files::isRegularFile).sorted().toList()) {
                if(p.getFileName().toString().equals("SHA256SUMS")) continue;
                var md=MessageDigest.getInstance("SHA-256");
                try(var in=Files.newInputStream(p)) { byte[] b=new byte[65536]; int n; while((n=in.read(b))!=-1) md.update(b,0,n); }
                lines.add(HexFormat.of().formatHex(md.digest())+"  "+out.relativize(p));
            }
        }
        Files.write(out.resolve("SHA256SUMS"),lines);
    }
}
