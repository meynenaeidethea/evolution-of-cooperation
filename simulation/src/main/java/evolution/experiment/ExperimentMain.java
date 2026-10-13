package evolution.experiment;

import evolution.simulation.SimulationConfig;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/** Separate entry point; the existing simulation and visual main classes remain independent. */
public final class ExperimentMain {
    private ExperimentMain() {}

    public static void main(String[] args) throws IOException {
        execute(args, SimulationConfig.baseline(), System.out);
    }

    static void execute(String[] args, SimulationConfig baseline, PrintStream output) throws IOException {
        var options = ExperimentOptions.parse(args);
        if (options.help()) {
            output.println("Запуск серий экспериментов:");
            output.println("  --series <" + Arrays.stream(ExperimentPlan.Series.values())
                    .map(ExperimentPlan.Series::option).collect(Collectors.joining("|")) + ">  (по умолчанию baseline)");
            output.println("  --repetitions <N>  Число повторов, seed = 1…N (по умолчанию 30)");
            output.println("  --output <path>    Новый каталог результатов");
            output.println("  --list             Только показать план, без расчёта и записи файлов");
            output.println("  --help             Показать справку");
            return;
        }
        var scenarios = ExperimentPlan.scenarios(baseline, options.series());
        output.printf(Locale.ROOT, "Серия: %s. Сценариев: %d. Повторов: %d. Всего запусков: %d.%n",
                options.series().option(), scenarios.size(), options.repetitions(),
                (long) scenarios.size() * options.repetitions());
        output.printf(Locale.ROOT, "Для каждого сценария seed = номер повтора: 1…%d.%n", options.repetitions());
        for (var scenario : scenarios) output.println(scenario.id() + ": " + scenario.config());
        if (options.repetitions() < ExperimentSeriesRunner.DEFAULT_REPETITIONS) {
            output.println("Это проверочная серия: план исследования требует минимум 30 повторов на сценарий.");
        }
        if (options.listOnly()) return;
        Path directory = options.outputDirectory();
        if (directory == null) {
            String timestamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").format(LocalDateTime.now());
            directory = Path.of("results", "experiments", options.series().option() + "-" + timestamp);
        }
        output.println("Результаты: " + directory.toAbsolutePath().normalize());
        new ExperimentSeriesRunner().run(scenarios, options.repetitions(), directory, update -> {
            var run = update.run();
            output.printf(Locale.ROOT, "[%d/%d] %s, повтор %d, seed %d: %s, поколений %d%n",
                    update.completedRuns(), update.totalRuns(), run.scenarioId(), run.repetition(),
                    run.result().seed(), run.result().status(), run.result().generations().size());
        });
        output.println("Серия завершена. Общие CSV и CSV по сценариям сохранены.");
    }
}
