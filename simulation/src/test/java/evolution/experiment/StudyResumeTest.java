package evolution.experiment;

import evolution.simulation.ConfigProfiles;
import evolution.simulation.CsvResultsWriter;
import evolution.simulation.ExperimentRun;
import evolution.simulation.SimulationRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StudyResumeTest {
    @TempDir Path temp;

    private Path profile() throws Exception {
        Path path = temp.resolve("custom.properties");
        String content = Files.readString(Path.of("profiles/demo.properties"))
                .replace("maximumGenerations=10", "maximumGenerations=2")
                .replace("ticksPerGeneration=30", "ticksPerGeneration=3");
        Files.writeString(path, content);
        return path;
    }

    private String[] args(Path profile, Path out, boolean resume) {
        var args = new ArrayList<>(List.of("--profile", profile.toString(), "--output", out.toString(),
                "--repetitions", "2", "--seed-start", "800001", "--threads", resume ? "1" : "2",
                "--scenarios", "baseline,global-matched"));
        if (resume) args.add("--resume");
        return args.toArray(String[]::new);
    }

    @Test void resumesMissingRunAndPartialAggregatesWithoutRewritingCompletedCheckpoint() throws Exception {
        Path profile = profile(), out = temp.resolve("series");
        StudyMain.main(args(profile, out, false));
        Map<String, byte[]> expected = new HashMap<>();
        for (String name : StudyStorage.TABLES) expected.put(name, Files.readAllBytes(out.resolve(name)));
        Path kept = out.resolve("checkpoints/baseline-1");
        String keptExecution = Files.readString(kept.resolve("execution.properties"));
        Files.delete(out.resolve("completed.txt")); // Simulate interruption before finalization.
        Path missing = out.resolve("checkpoints/global-matched-2");
        Files.move(missing, temp.resolve("incomplete-run"));
        Files.writeString(out.resolve("runs.csv"), "partial aggregate");
        StudyMain.main(args(profile, out, true));
        assertEquals(keptExecution, Files.readString(kept.resolve("execution.properties")));
        for (String name : StudyStorage.TABLES) assertArrayEquals(expected.get(name), Files.readAllBytes(out.resolve(name)));
        assertThrows(IllegalArgumentException.class, () -> StudyMain.main(args(profile, out, true)));
        // Independent batch export matches the checkpoint-assembled CSV byte for byte.
        var runs = new ArrayList<ExperimentRun>();
        for (var scenario : StudyMain.scenarios(ConfigProfiles.load(profile))) {
            if (!Set.of("baseline", "global-matched").contains(scenario.id())) continue;
            for (int rep = 1; rep <= 2; rep++)
                runs.add(new ExperimentRun(scenario.id(), rep, new SimulationRunner().run(
                        scenario.config(), 800000 + rep, scenario.pairingMode())));
        }
        Path reference = temp.resolve("reference");
        CsvResultsWriter.writeExperiments(reference, runs);
        for (String name : StudyStorage.TABLES) assertArrayEquals(Files.readAllBytes(reference.resolve(name)), expected.get(name));
    }

    @Test void rejectsChangedConfigurationAndCorruptedOrIncompleteCheckpoints() throws Exception {
        Path profile = profile(), out = temp.resolve("series");
        StudyMain.main(args(profile, out, false));
        Files.delete(out.resolve("completed.txt"));
        String original = Files.readString(profile);
        Files.writeString(profile, original.replace("populationSize=160", "populationSize=159"));
        assertThrows(IllegalArgumentException.class, () -> StudyMain.main(args(profile, out, true)));
        Files.writeString(profile, original);
        Path csv = out.resolve("checkpoints/baseline-1/runs.csv");
        Files.writeString(csv, "corrupt", StandardOpenOption.APPEND);
        assertThrows(java.io.IOException.class, () -> StudyMain.main(args(profile, out, true)));
        Files.delete(out.resolve("checkpoints/baseline-1/identity.txt"));
        assertThrows(java.io.IOException.class, () -> StudyMain.main(args(profile, out, true)));
    }

    @Test void rejectsInvalidArgumentsBeforeCreatingOutput() throws Exception {
        Path profile = profile(), out = temp.resolve("invalid");
        assertThrows(IllegalArgumentException.class, () -> StudyMain.main(new String[]{"--resume"}));
        assertThrows(IllegalArgumentException.class, () -> StudyMain.main(new String[]{"--unknown", "1"}));
        assertThrows(IllegalArgumentException.class, () -> StudyMain.main(new String[]{"--profile", profile.toString(),
                "--output", out.toString(), "--scenarios", "unknown"}));
        assertThrows(ArithmeticException.class, () -> StudyMain.main(new String[]{"--profile", profile.toString(),
                "--output", out.toString(), "--seed-start", Long.toString(Long.MAX_VALUE), "--repetitions", "2"}));
        assertFalse(Files.exists(out));
    }
}
