package evolution.experiment;

import evolution.simulation.CsvResultsWriter;
import evolution.simulation.ExperimentRun;
import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Atomic checkpoints and deterministic CSV assembly, without rerunning completed simulations. */
final class StudyStorage {
    static final List<String> TABLES = List.of("runs.csv", "generations.csv", "genotype-frequencies.csv");
    private static final Set<String> CHECKPOINT_FILES = Set.of("runs.csv", "generations.csv",
            "genotype-frequencies.csv", "execution.properties", "identity.txt");

    private StudyStorage() {}

    static String digest(Path path) throws Exception {
        var md = MessageDigest.getInstance("SHA-256");
        try (var in = Files.newInputStream(path)) {
            byte[] bytes = new byte[65536];
            int n;
            while ((n = in.read(bytes)) != -1) md.update(bytes, 0, n);
        }
        return HexFormat.of().formatHex(md.digest());
    }

    static String implementationFingerprint() throws Exception {
        var lines = new ArrayList<String>();
        try (var paths = Files.walk(Path.of("simulation/src/main"))) {
            for (Path p : paths.filter(Files::isRegularFile).sorted().toList()) {
                lines.add(p + "=" + digest(p));
            }
        }
        return String.join("\n", lines);
    }

    static void captureSource(Path out) throws IOException {
        Path source = out.resolve("source");
        for (String dir : List.of("simulation/src", "profiles", "tools", "docs", "gradle")) {
            try (var paths = Files.walk(Path.of(dir))) {
                for (Path p : paths.filter(Files::isRegularFile)
                        .filter(p -> !p.toString().contains("__pycache__")).sorted().toList()) {
                    Path dest = source.resolve(p);
                    Files.createDirectories(dest.getParent());
                    Files.copy(p, dest);
                }
            }
        }
        for (String file : List.of("simulation/build.gradle.kts", "settings.gradle.kts", "gradle.properties",
                "gradlew", "gradlew.bat")) {
            Path dest = source.resolve(file);
            Files.createDirectories(dest.getParent());
            Files.copy(Path.of(file), dest);
        }
    }

    static String identity(ExperimentScenario scenario, int repetition, long seed) {
        return scenario.id() + "\n" + repetition + "\n" + seed + "\n"
                + scenario.config() + "\n" + scenario.pairingMode() + "\n";
    }

    static void checkpoint(Path target, ExperimentRun run, String identity, double seconds) throws Exception {
        Path pending = Files.createTempDirectory(target.getParent(), ".pending-");
        CsvResultsWriter.writeExperiments(pending, List.of(run));
        Files.writeString(pending.resolve("identity.txt"), identity);
        Files.writeString(pending.resolve("execution.properties"), "duration_seconds=" + seconds
                + "\nelapsed_ticks=" + run.result().generations().stream().mapToLong(m -> m.ticks()).sum() + "\nschema=2\n");
        checksums(pending);
        try { Files.move(pending, target, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException e) { Files.move(pending, target); }
    }

    static void validateCheckpoint(Path path, String expected) throws Exception {
        if (!Files.readString(path.resolve("identity.txt")).equals(expected)) {
            throw new IOException("Checkpoint belongs to a different run: " + path);
        }
        var found = new HashSet<String>();
        for (String line : Files.readAllLines(path.resolve("SHA256SUMS"))) {
            String[] parts = line.split("  ", 2);
            if (parts.length != 2 || !CHECKPOINT_FILES.contains(parts[1]) || !found.add(parts[1])
                    || !digest(path.resolve(parts[1])).equals(parts[0])) {
                throw new IOException("Invalid checkpoint checksum: " + path + " / " + line);
            }
        }
        if (!found.equals(CHECKPOINT_FILES)) throw new IOException("Incomplete checkpoint: " + path);
    }

    static void merge(Path out, List<Path> checkpoints) throws IOException {
        Path staging = Files.createTempDirectory(out, ".merge-");
        for (String name : TABLES) {
            try (var writer = Files.newBufferedWriter(staging.resolve(name))) {
                String header = null;
                for (Path checkpoint : checkpoints) {
                    try (var reader = Files.newBufferedReader(checkpoint.resolve(name))) {
                        String current = reader.readLine();
                        if (current == null) throw new IOException("Empty checkpoint CSV: " + checkpoint);
                        if (header == null) {
                            header = current;
                            writer.write(header); writer.newLine();
                        } else if (!header.equals(current)) throw new IOException("CSV schema differs: " + checkpoint);
                        String row;
                        while ((row = reader.readLine()) != null) { writer.write(row); writer.newLine(); }
                    }
                }
            }
        }
        // Completed series never enter this method. Partial aggregates can be rebuilt after a crash.
        for (String name : TABLES) {
            Files.move(staging.resolve(name), out.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        }
        Files.delete(staging);
    }

    static void checksums(Path out) throws Exception {
        var lines = new ArrayList<String>();
        try (var paths = Files.walk(out)) {
            for (Path p : paths.filter(Files::isRegularFile).sorted().toList()) {
                Path relative = out.relativize(p);
                if (p.getFileName().toString().equals("SHA256SUMS") || p.getFileName().toString().equals(".lock") || p.getFileName().toString().equals(".completion-pending")
                        || relative.toString().contains(".pending-") || relative.toString().contains(".merge-")) continue;
                lines.add(digest(p) + "  " + relative);
            }
        }
        Files.write(out.resolve("SHA256SUMS"), lines);
    }
}
