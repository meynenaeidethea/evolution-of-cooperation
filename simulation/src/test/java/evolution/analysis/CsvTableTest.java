package evolution.analysis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class CsvTableTest {
    @TempDir Path directory;
    @Test
    void readsBomCrLfEscapedQuotesAndEmptyLastCell() throws Exception {
        Path file = directory.resolve("sample.csv");
        Files.writeString(file, "\uFEFFname,value,empty\r\n\"a,\"\"b\"\"\nline\",2,\r\n");
        var table = CsvTable.read(file);
        assertEquals("a,\"b\"\nline", table.rows().getFirst().get("name"));
        assertEquals("", table.rows().getFirst().get("empty"));
        assertEquals(1, table.rows().size());
    }
    @Test
    void rejectsMalformedQuotesDuplicateHeadersAndTruncatedRows() throws Exception {
        Path file = directory.resolve("bad.csv");
        for (String text : new String[]{"a,a\n1,2\n", "a,b\n1\n", "a,b\n\"x", "a,b\n\"x\"y,2\n", "a,b\nx\"y,2\n"}) {
            Files.writeString(file, text);
            assertThrows(IOException.class, () -> CsvTable.read(file));
        }
    }
}
