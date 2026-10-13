package evolution.analysis;

import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.XYSeries;
import org.knowm.xchart.style.markers.SeriesMarkers;

import java.awt.Color;
import java.awt.Font;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/** Standalone HTML plus PNG and machine-readable statistics. Does not overwrite existing reports. */
public final class SeriesReportWriter {
    private SeriesReportWriter() {}

    public static void write(SeriesAnalysis.Report report, Path directory) throws IOException {
        Path output = directory.toAbsolutePath().normalize();
        Files.createDirectories(output.getParent());
        Files.createDirectory(output);
        StringBuilder html = new StringBuilder("""
                <!doctype html><html lang="ru"><meta charset="utf-8"><title>Анализ серии</title>
                <style>body{font:16px sans-serif;max-width:1200px;margin:32px auto;padding:0 20px;color:#243044}
                table{border-collapse:collapse;font-size:14px;margin:20px 0;width:100%}td,th{border:1px solid #ccd3df;padding:8px;text-align:right}
                td:first-child,th:first-child{text-align:left}img{max-width:100%;height:auto}code{overflow-wrap:anywhere}
                .scroll{overflow:auto}.note{background:#edf3fa;padding:16px}h2{margin-top:40px}</style>
                <h1>Сравнение сценариев</h1>
                """);
        html.append("<p>Источник: <code>").append(escape(report.source().toString())).append("</code>. Горизонт: ")
                .append(report.horizon()).append(" поколений.</p>");
        html.append("""
                <div class="note">Единица наблюдения — отдельный seed, а не агент или поколение.
                Интервалы средних: 95% percentile bootstrap (20 000 выборок, фиксированный seed 20261013).
                Для доли завершённых запусков — интервал Уилсона. Это разведочный анализ без поправки на множественные сравнения.
                Вымирание означает нулевую выживаемость, но <em>не</em> нулевую кооперацию или энергию:
                для них показаны только определённые наблюдения и их число n. Различия кооперации относительно baseline
                вычисляются только для пар seed, в которых оба запуска завершились; это условный результат среди выживших.</div>
                <h2>Конечные значения</h2><p>Кооперация и выживаемость — проценты. В скобках 95% интервал; n — число наблюдений.</p>
                <div class="scroll"><table><tr><th>Сценарий</th><th>Завершение</th><th>Выживаемость</th><th>Кооперация</th><th>Энергия</th></tr>
                """);
        StringBuilder summary = new StringBuilder("scenario,metric,n,mean,ci_low,ci_high\n");
        for (var scenario : report.scenarios()) {
            html.append("<tr><td>").append(escape(scenario.id())).append("</td>");
            cell(html, scenario.completion(), 100);
            cell(html, scenario.survival(), 100);
            cell(html, scenario.cooperation(), 100);
            cell(html, scenario.energy(), 1);
            html.append("</tr>");
            row(summary, scenario.id(), "completion", scenario.completion());
            row(summary, scenario.id(), "survival", scenario.survival());
            row(summary, scenario.id(), "cooperation", scenario.cooperation());
            row(summary, scenario.id(), "energy", scenario.energy());
        }
        html.append("</table></div>");
        endpoint(html, output, report, "survival", "Survival at final generation (%)", SeriesAnalysis.Scenario::survival);
        endpoint(html, output, report, "cooperation", "Cooperation at final generation (%)", SeriesAnalysis.Scenario::cooperation);
        html.append("<h2>Парные эффекты относительно baseline</h2><p>Разность в процентных пунктах, одинаковые seed. Ноль означает отсутствие различия.</p>");
        html.append("<table><tr><th>Сценарий</th><th>Δ выживаемости</th><th>Δ кооперации</th></tr>");
        StringBuilder effects = new StringBuilder("scenario,metric,n,mean,ci_low,ci_high\n");
        for (var effect : report.effects()) {
            html.append("<tr><td>").append(escape(effect.scenario())).append("</td>");
            cell(html, effect.survival(), 100); cell(html, effect.cooperation(), 100);
            html.append("</tr>");
            row(effects, effect.scenario(), "survival", effect.survival());
            row(effects, effect.scenario(), "cooperation", effect.cooperation());
        }
        html.append("</table><h2>Динамика поколений</h2><p>Средние по seed; интервалы и n сохранены в trajectories.csv. Отсутствующие значения не заменяются нулями.</p>");
        StringBuilder trajectories = new StringBuilder("scenario,generation,metric,n,mean,ci_low,ci_high\n");
        for (var scenario : report.scenarios()) for (var point : scenario.trajectory()) {
            row(trajectories, scenario.id() + "," + point.generation(), "survival", point.survival(), false);
            row(trajectories, scenario.id() + "," + point.generation(), "cooperation", point.cooperation(), false);
            row(trajectories, scenario.id() + "," + point.generation(), "energy", point.energy(), false);
        }
        // A few curves per chart, rather than all scenario lines in a single unreadable plot.
        var families = report.scenarios().stream().filter(s -> !s.id().equals("baseline"))
                .map(s -> family(s.id())).distinct().toList();
        if (families.isEmpty()) families = List.of("baseline");
        int figure = 0;
        for (String family : families) {
            var scenarios = report.scenarios().stream().filter(s -> s.id().equals("baseline") || family(s.id()).equals(family)).toList();
            html.append("<h3>").append(escape(family)).append("</h3>");
            trajectory(html, output, scenarios, "trajectory-" + (++figure), "Survival (%)", SeriesAnalysis.Trajectory::survival, 100);
            trajectory(html, output, scenarios, "trajectory-" + (++figure), "Cooperation (%)", SeriesAnalysis.Trajectory::cooperation, 100);
        }
        html.append("<h2>Частоты генотипов в последнем поколении</h2><p>Средние частоты по завершённым seed, не объединённый подсчёт всех агентов. Пусто — нет завершённых запусков.</p>");
        html.append("<div class=\"scroll\"><table><tr><th>Сценарий</th>");
        var genes = java.util.stream.IntStream.range(0, 32).mapToObj(i -> {
            StringBuilder gene = new StringBuilder();
            for (int bit = 4; bit >= 0; bit--) gene.append((i & (1 << bit)) == 0 ? 'C' : 'D');
            return gene.toString();
        }).toList();
        genes.forEach(g -> html.append("<th>").append(g).append("</th>"));
        html.append("</tr>");
        for (var scenario : report.scenarios()) {
            html.append("<tr><td>").append(escape(scenario.id())).append("</td>");
            for (String gene : genes) {
                Double frequency = scenario.genotypeFrequencies().get(gene);
                html.append("<td style=\"background:rgba(45,120,210,")
                        .append(frequency == null ? "0" : format(frequency)).append(")\">")
                        .append(frequency == null ? "—" : format(frequency * 100)).append("</td>");
            }
            html.append("</tr>");
        }
        html.append("</table></div><h2>Параметры и происхождение данных</h2><p>Полная конфигурация первого запуска каждого сценария:</p>");
        for (var scenario : report.scenarios()) html.append("<p><b>").append(escape(scenario.id())).append("</b>: <code>")
                .append(escape(new java.util.TreeMap<>(scenario.runs().getFirst().metadata()).toString())).append("</code></p>");
        StringBuilder hashes = new StringBuilder();
        for (String file : List.of("runs.csv", "generations.csv", "genotype-frequencies.csv")) {
            try {
                String hash = java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(report.source().resolve(file))));
                hashes.append(hash).append("  ").append(file).append('\n');
            } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
        }
        html.append("<pre>").append(hashes).append("</pre></html>");
        Files.writeString(output.resolve("summary.csv"), summary);
        Files.writeString(output.resolve("paired-effects.csv"), effects);
        Files.writeString(output.resolve("trajectories.csv"), trajectories);
        Files.writeString(output.resolve("source.sha256"), hashes);
        Files.writeString(output.resolve("report.html"), html);
    }

    private static XYChart chart(String title, String x, String y, int height) {
        var chart = new XYChartBuilder().width(1100).height(height).title(title).xAxisTitle(x).yAxisTitle(y).build();
        chart.getStyler().setBaseFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        chart.getStyler().setChartBackgroundColor(Color.WHITE);
        return chart;
    }

    private static void endpoint(StringBuilder html, Path output, SeriesAnalysis.Report report, String name, String title,
                                 Function<SeriesAnalysis.Scenario, BootstrapStatistics.Estimate> metric) throws IOException {
        var chart = chart(title, "%", "Scenario (table order)", Math.max(500, report.scenarios().size() * 35));
        chart.getStyler().setLegendVisible(false);
        chart.getStyler().setYAxisMin(0.0);
        chart.getStyler().setYAxisMax((double) report.scenarios().size() + 1);
        chart.getStyler().setYAxisTickMarkSpacingHint(25);
        chart.getStyler().setYAxisTickLabelsFormattingFunction(value -> {
            int index = (int) Math.round(value) - 1;
            return Math.abs(value - Math.rint(value)) < .01 && index >= 0 && index < report.scenarios().size()
                    ? report.scenarios().get(index).id() : Integer.toString((int) Math.round(value));
        });
        for (int i = 0; i < report.scenarios().size(); i++) {
            var estimate = metric.apply(report.scenarios().get(i));
            if (estimate.n() == 0) continue;
            double y = i + 1;
            chart.addSeries("ci" + i, new double[]{estimate.low() * 100, estimate.high() * 100}, new double[]{y, y})
                    .setMarker(SeriesMarkers.NONE).setLineColor(new Color(40, 95, 160));
            var point = chart.addSeries("mean" + i, new double[]{estimate.mean() * 100}, new double[]{y});
            point.setXYSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Scatter);
            point.setMarker(SeriesMarkers.DIAMOND);
        }
        embed(html, output, name, chart);
    }

    private static void trajectory(StringBuilder html, Path output, List<SeriesAnalysis.Scenario> scenarios, String name,
                                   String title, Function<SeriesAnalysis.Trajectory, BootstrapStatistics.Estimate> metric, double scale) throws IOException {
        var chart = chart(title, "Generation", title, 500);
        chart.getStyler().setYAxisMin(0.0);
        chart.getStyler().setYAxisMax(100.0);
        for (var scenario : scenarios) {
            var defined = scenario.trajectory().stream().filter(p -> metric.apply(p).n() > 0).toList();
            if (defined.isEmpty()) continue;
            chart.addSeries(scenario.id(), defined.stream().mapToDouble(SeriesAnalysis.Trajectory::generation).toArray(),
                    defined.stream().mapToDouble(p -> metric.apply(p).mean() * scale).toArray());
        }
        embed(html, output, name, chart);
    }

    private static void embed(StringBuilder html, Path output, String name, XYChart chart) throws IOException {
        if (chart.getSeriesCollection().isEmpty()) { html.append("<p>Нет определённых наблюдений для графика.</p>"); return; }
        byte[] png = BitmapEncoder.getBitmapBytes(chart, BitmapEncoder.BitmapFormat.PNG);
        Files.write(output.resolve(name + ".png"), png);
        html.append("<img alt=\"").append(escape(chart.getTitle())).append("\" src=\"data:image/png;base64,")
                .append(Base64.getEncoder().encodeToString(png)).append("\">");
    }

    private static String family(String id) {
        int dash = id.lastIndexOf('-');
        return dash < 0 ? id : id.substring(0, dash);
    }
    private static void cell(StringBuilder html, BootstrapStatistics.Estimate value, double scale) {
        html.append("<td>").append(value.n() == 0 ? "—; n=0" : format(value.mean() * scale) + " ["
                + format(value.low() * scale) + "; " + format(value.high() * scale) + "]; n=" + value.n()).append("</td>");
    }
    private static void row(StringBuilder csv, String id, String metric, BootstrapStatistics.Estimate estimate) {
        row(csv, id, metric, estimate, true);
    }
    private static void row(StringBuilder csv, String id, String metric, BootstrapStatistics.Estimate estimate, boolean quote) {
        csv.append(quote ? csv(id) : csv(id.substring(0, id.lastIndexOf(','))) + id.substring(id.lastIndexOf(',')))
                .append(',').append(metric).append(',').append(estimate.n()).append(',')
                .append(number(estimate.mean())).append(',').append(number(estimate.low())).append(',').append(number(estimate.high())).append('\n');
    }
    private static String number(double value) { return Double.isFinite(value) ? Double.toString(value) : ""; }
    private static String format(double value) { return String.format(Locale.ROOT, "%.3f", value); }
    private static String csv(String text) { return "\"" + text.replace("\"", "\"\"") + "\""; }
    private static String escape(String text) { return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"); }
}
