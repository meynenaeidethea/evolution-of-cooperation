package evolution.experiment;

import java.nio.file.Path;
import java.util.HashSet;

public record ExperimentOptions(ExperimentPlan.Series series, int repetitions, Path outputDirectory,
                                boolean listOnly, boolean help) {
    public static ExperimentOptions parse(String[] args) {
        var series = ExperimentPlan.Series.BASELINE;
        int repetitions = ExperimentSeriesRunner.DEFAULT_REPETITIONS;
        Path output = null;
        boolean listOnly = false;
        boolean help = false;
        var seen = new HashSet<String>();
        for (int index = 0; index < args.length; index++) {
            String option = args[index];
            if (!seen.add(option)) throw new IllegalArgumentException("Duplicate option: " + option);
            switch (option) {
                case "--help" -> help = true;
                case "--list" -> listOnly = true;
                case "--series", "--repetitions", "--output" -> {
                    if (index + 1 == args.length || args[index + 1].startsWith("--")) {
                        throw new IllegalArgumentException("Missing value for " + option);
                    }
                    String value = args[++index];
                    switch (option) {
                        case "--series" -> series = ExperimentPlan.Series.parse(value);
                        case "--repetitions" -> {
                            try {
                                repetitions = Integer.parseInt(value);
                            } catch (NumberFormatException exception) {
                                throw new IllegalArgumentException("Repetitions must be a positive integer", exception);
                            }
                            if (repetitions < 1) throw new IllegalArgumentException("Repetitions must be positive");
                        }
                        case "--output" -> {
                            if (value.isBlank()) throw new IllegalArgumentException("Output path must not be blank");
                            output = Path.of(value);
                        }
                    }
                }
                default -> throw new IllegalArgumentException("Unknown option: " + option);
            }
        }
        return new ExperimentOptions(series, repetitions, output, listOnly, help);
    }
}
