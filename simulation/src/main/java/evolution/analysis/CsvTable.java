package evolution.analysis;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** UTF-8 CSV reader with quoted delimiters, escaped quotes and multiline fields. */
record CsvTable(List<String> columns, List<Map<String, String>> rows) {
    CsvTable {
        columns = List.copyOf(columns);
        rows = List.copyOf(rows);
    }

    static CsvTable read(Path path) throws IOException {
        String text = Files.readString(path);
        var records = new ArrayList<List<String>>();
        var row = new ArrayList<String>();
        var cell = new StringBuilder();
        boolean quoted = false;
        boolean closed = false;
        for (int index = text.startsWith("\uFEFF") ? 1 : 0; index < text.length(); index++) {
            char ch = text.charAt(index);
            if (quoted) {
                if (ch == '"') {
                    if (index + 1 < text.length() && text.charAt(index + 1) == '"') {
                        cell.append('"');
                        index++;
                    } else {
                        quoted = false;
                        closed = true;
                    }
                } else cell.append(ch);
            } else if (ch == ',' || ch == '\n' || ch == '\r') {
                row.add(cell.toString());
                cell.setLength(0);
                closed = false;
                if (ch != ',') {
                    records.add(List.copyOf(row));
                    row.clear();
                    if (ch == '\r' && index + 1 < text.length() && text.charAt(index + 1) == '\n') index++;
                }
            } else if (ch == '"' && cell.isEmpty() && !closed) {
                quoted = true;
            } else {
                if (closed || ch == '"') throw new IOException("Malformed CSV quoting: " + path);
                cell.append(ch);
            }
        }
        if (quoted) throw new IOException("Unterminated CSV field: " + path);
        if (!row.isEmpty() || !cell.isEmpty() || closed) {
            row.add(cell.toString());
            records.add(List.copyOf(row));
        }
        if (records.isEmpty()) throw new IOException("Empty CSV: " + path);
        var headers = records.getFirst();
        if (headers.stream().anyMatch(String::isBlank) || new HashSet<>(headers).size() != headers.size()) {
            throw new IOException("Empty or duplicate CSV headers: " + path);
        }
        var rows = new ArrayList<Map<String, String>>();
        for (int index = 1; index < records.size(); index++) {
            var record = records.get(index);
            if (record.size() != headers.size()) throw new IOException("Wrong CSV column count in record " + (index + 1) + ": " + path);
            var values = new LinkedHashMap<String, String>();
            for (int column = 0; column < headers.size(); column++) values.put(headers.get(column), record.get(column));
            rows.add(Map.copyOf(values));
        }
        return new CsvTable(headers, rows);
    }

    void require(String... names) throws IOException {
        for (String name : names) {
            if (!columns.contains(name)) throw new IOException("Missing CSV column: " + name);
        }
    }
}
