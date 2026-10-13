package evolution.analysis;

import evolution.simulation.CsvResultsWriter;
import evolution.simulation.ExperimentRun;
import evolution.simulation.SimulationConfig;
import evolution.simulation.SimulationRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SeriesReportWriterTest {
    @TempDir Path directory;

    private Path source(String scenario, boolean extinct) throws Exception {
        Path source = directory.resolve("source");
        var config = extinct ? new SimulationConfig(3, 1, 5, 1, 100, 0, 100, 0, 0, 0, 0, 1, 2)
                : new SimulationConfig(3, 4, 2, 1, 1000, 400, 100, .3, 20, .8, .01, 3, 2);
        CsvResultsWriter.writeExperiments(source, List.of(new ExperimentRun(scenario, 1, new SimulationRunner().run(config, 7))));
        return source;
    }

    @Test
    void writesStandaloneHtmlPngAndReadableCsvWithoutChangingSource() throws Exception {
        Path source = source("<sample>, \"quoted\"\nnext", false);
        String original = Files.readString(source.resolve("runs.csv"));
        Path output = directory.resolve("report");
        SeriesReportWriter.write(SeriesAnalysis.read(source), output);
        String html = Files.readString(output.resolve("report.html"));
        assertTrue(html.contains("&lt;sample&gt;"));
        assertFalse(html.contains("<sample>"));
        assertTrue(html.contains("data:image/png;base64,"));
        var png = ImageIO.read(output.resolve("survival.png").toFile());
        assertEquals(1100, png.getWidth());
        assertEquals(4, CsvTable.read(output.resolve("summary.csv")).rows().size());
        assertEquals(6, CsvTable.read(output.resolve("trajectories.csv")).rows().size());
        assertEquals(3, Files.readAllLines(output.resolve("source.sha256")).size());
        assertEquals(original, Files.readString(source.resolve("runs.csv")));
        assertThrows(FileAlreadyExistsException.class, () -> SeriesReportWriter.write(SeriesAnalysis.read(source), output));
        assertEquals(html, Files.readString(output.resolve("report.html")));
    }

    @Test
    void rendersExtinctionWithoutFakeZeroCooperation() throws Exception {
        Path output = directory.resolve("report");
        SeriesReportWriter.write(SeriesAnalysis.read(source("dying", true)), output);
        assertFalse(Files.exists(output.resolve("cooperation.png")));
        assertTrue(Files.readString(output.resolve("report.html")).contains("—; n=0"));
        var rows = CsvTable.read(output.resolve("summary.csv")).rows();
        assertEquals("", rows.stream().filter(r -> r.get("metric").equals("cooperation")).findFirst().orElseThrow().get("mean"));
    }

    @Test
    void cliRejectsMalformedInputBeforeCreatingOutput() throws Exception {
        Path output = directory.resolve("report");
        assertThrows(java.io.IOException.class, () -> AnalysisMain.main(new String[]{"--input", directory.toString(), "--output", output.toString()}));
        assertFalse(Files.exists(output));
        assertThrows(IllegalArgumentException.class, () -> AnalysisMain.main(new String[]{"--input"}));
        assertThrows(IllegalArgumentException.class, () -> AnalysisMain.main(new String[]{"--other", "value"}));
    }
}
