package evolution.analysis;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Offline entry point: reads completed CSV, never reruns or changes the simulation. */
public final class AnalysisMain {
    private AnalysisMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("--help")) {
            System.out.println("Usage: --input <series-directory> [--output <new-report-directory>]");
            return;
        }
        Path input = null;
        Path output = null;
        for (int i = 0; i < args.length; i += 2) {
            if (i + 1 == args.length) throw new IllegalArgumentException("Missing value: " + args[i]);
            switch (args[i]) {
                case "--input" -> {
                    if (input != null) throw new IllegalArgumentException("Repeated --input");
                    input = Path.of(args[i + 1]);
                }
                case "--output" -> {
                    if (output != null) throw new IllegalArgumentException("Repeated --output");
                    output = Path.of(args[i + 1]);
                }
                default -> throw new IllegalArgumentException("Unknown option: " + args[i]);
            }
        }
        if (input == null) throw new IllegalArgumentException("Required: --input <series-directory>");
        if (output == null) output = Path.of("results", "analysis", input.toAbsolutePath().getFileName() + "-"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")));
        var report = SeriesAnalysis.read(input);
        SeriesReportWriter.write(report, output);
        System.out.println("Отчёт сохранён: " + output.toAbsolutePath().resolve("report.html"));
    }
}
