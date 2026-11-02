package evolution.experiment;

import evolution.simulation.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Reproducible study runner. --resume reuses only complete, compatible, checksum-verified runs. */
public final class StudyMain {
    public static List<ExperimentScenario> scenarios(SimulationConfig config) {
        return StudyScenarios.create(config);
    }

    public static void main(String[] args) throws Exception {
        var options = new LinkedHashMap<String, String>();
        boolean resume = false;
        for (int i = 0; i < args.length; i++) {
            String option = args[i];
            if (option.equals("--help")) {
                System.out.println("runStudy [--profile FILE] [--output NEW_DIR] [--repetitions N] "
                        + "[--seed-start LONG] [--threads 1..32] [--scenarios IDS] [--resume]");
                return;
            }
            if (option.equals("--resume")) {
                if (resume) throw new IllegalArgumentException("Duplicate --resume");
                resume = true;
            } else if (!Set.of("--profile", "--output", "--repetitions", "--seed-start", "--threads", "--scenarios").contains(option)
                    || i + 1 == args.length || options.putIfAbsent(option, args[++i]) != null) {
                throw new IllegalArgumentException("Expected unique option/value pairs and optional --resume");
            }
        }
        if (resume && !options.containsKey("--output")) throw new IllegalArgumentException("--resume requires --output");
        Path profile = Path.of(options.getOrDefault("--profile", "profiles/research.properties"));
        var config = ConfigProfiles.load(profile);
        int n = Integer.parseInt(options.getOrDefault("--repetitions", "40"));
        long start = Long.parseLong(options.getOrDefault("--seed-start", "10001"));
        int threads = Integer.parseInt(options.getOrDefault("--threads", "2"));
        if (n < 1 || threads < 1 || threads > 32) throw new IllegalArgumentException("Positive repetitions; threads 1..32");
        Math.addExact(start, n - 1L);
        var scenarios = scenarios(config);
        if (options.containsKey("--scenarios")) {
            var ids = new HashSet<>(Arrays.asList(options.get("--scenarios").split(",", -1)));
            scenarios = scenarios.stream().filter(s -> ids.remove(s.id())).toList();
            if (!ids.isEmpty() || scenarios.isEmpty()) throw new IllegalArgumentException("Unknown/empty scenarios: " + ids);
        }
        Path out = Path.of(options.getOrDefault("--output", "results/study-" + System.currentTimeMillis()));
        String plan = "model=" + SimulationSession.MODEL_VERSION + "\njava=" + Runtime.version()
                + "\nprng=" + SimulationSession.RANDOM_GENERATOR + "\nrepetitions=" + n + "\nseed_start=" + start
                + "\nscenarios=" + scenarios + "\nsource=\n" + StudyStorage.implementationFingerprint() + "\n";
        if (!resume) {
            Files.createDirectories(out.toAbsolutePath().getParent());
            Files.createDirectory(out);
        } else if (!Files.isDirectory(out)) throw new IllegalArgumentException("Missing series to resume: " + out);

        try (var channel = FileChannel.open(out.resolve(".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var lock = channel.tryLock()) {
            if (lock == null) throw new IllegalStateException("Series is already running: " + out);
            if (resume) {
                if (!Files.readString(out.resolve("plan.txt")).equals(plan)) {
                    throw new IllegalArgumentException("Cannot resume: parameters, seeds, Java or source differ");
                }
                if (Files.exists(out.resolve("completed.txt"))) throw new IllegalArgumentException("Series is already completed");
            } else {
                Files.writeString(out.resolve("plan.txt"), plan);
                Files.copy(profile, out.resolve("profile.properties"));
                Files.createDirectory(out.resolve("checkpoints"));
                String revision = git("rev-parse", "HEAD"), status = git("status", "--porcelain");
                Files.writeString(out.resolve("source.patch"), git("diff", "--binary"));
                Files.writeString(out.resolve("manifest.txt"), "schema=2\nrevision=" + revision.strip()
                        + "\ndirty=" + (status.equals("unavailable") ? "unknown" : !status.isBlank())
                        + "\nthreads=" + threads + "\nargs=" + Arrays.toString(args) + "\n" + plan);
                StudyStorage.captureSource(out);
            }
            // Validate all reusable checkpoints before scheduling any new work.
            var checkpoints = new ArrayList<Path>();
            var identities = new LinkedHashMap<Path, String>();
            for (var scenario : scenarios) for (int repetition = 1; repetition <= n; repetition++) {
                Path checkpoint = out.resolve("checkpoints").resolve(scenario.id() + "-" + repetition);
                String identity = StudyStorage.identity(scenario, repetition, Math.addExact(start, repetition - 1L));
                if (Files.exists(checkpoint)) StudyStorage.validateCheckpoint(checkpoint, identity);
                checkpoints.add(checkpoint); identities.put(checkpoint, identity);
            }
            long began = System.nanoTime();
            var futures = new ArrayList<Future<?>>();
            try (var pool = Executors.newFixedThreadPool(threads)) {
                for (var scenario : scenarios) for (int rep = 1; rep <= n; rep++) {
                    final int repetition = rep;
                    Path checkpoint = out.resolve("checkpoints").resolve(scenario.id() + "-" + rep);
                    if (Files.exists(checkpoint)) { System.out.println("Reusing " + checkpoint.getFileName()); continue; }
                    futures.add(pool.submit(() -> {
                        long t = System.nanoTime();
                        var result = new SimulationRunner().run(scenario.config(), Math.addExact(start, repetition - 1L), scenario.pairingMode());
                        StudyStorage.checkpoint(checkpoint, new ExperimentRun(scenario.id(), repetition, result),
                                identities.get(checkpoint), (System.nanoTime() - t) / 1e9);
                        System.out.println("Completed " + checkpoint.getFileName() + " seed=" + result.seed());
                        return null;
                    }));
                }
                try { for (var future : futures) future.get(); }
                catch (Exception e) { pool.shutdownNow(); throw e; }
            }
            StudyStorage.merge(out, checkpoints);
            StudyStorage.checksums(out);
            Files.writeString(out.resolve("completed.txt"), "last_invocation_seconds=" + (System.nanoTime() - began) / 1e9 + "\n");
            StudyStorage.checksums(out);
        }
    }

    private static String git(String... args) throws Exception {
        var command = new ArrayList<String>(); command.add("git"); command.addAll(List.of(args));
        var process = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        String result = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return process.waitFor() == 0 ? result : "unavailable";
    }

    public static void checksums(Path out) throws Exception { StudyStorage.checksums(out); }
}
