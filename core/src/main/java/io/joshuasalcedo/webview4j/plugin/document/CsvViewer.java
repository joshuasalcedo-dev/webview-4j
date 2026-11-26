package io.joshuasalcedo.webview4j.plugin.document;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A CSV (or TSV) file as a table: its columns sized to their text, a row number each, sorting by a header click,
 * search, zoom, arrow keys between cells, download. Only the rows in view are drawn, so long files scroll smoothly.
 * <pre>{@code
 * new CsvViewer().file(Path.of("customers.csv")).grow()
 * new CsvViewer().text("name,score\nAna,92\n").onCellSelect(cell -> chosen = cell.value())
 * }</pre>
 */
public final class CsvViewer extends SheetFile<CsvViewer> {

    /** A chosen cell: its row (0 is the first after the headers, in the file's order), its column, its text. */
    public record Cell(int row, int col, String value) {}

    public CsvViewer() {
        super("csvViewer", "text/csv;charset=utf-8");
    }

    /** The file's text itself. */
    public CsvViewer text(String csv) {
        return bytes((csv == null ? "" : csv).getBytes(StandardCharsets.UTF_8));
    }

    /** The first row names the columns (true unless false); else they are Column 1, 2... */
    public CsvViewer header(boolean header) {
        return prop("header", header);
    }

    /** The character between fields; guessed from the text (comma, tab, semicolon, bar) unless given. */
    public CsvViewer delimiter(char delimiter) {
        return prop("delimiter", String.valueOf(delimiter));
    }

    /** A cell was chosen (a click, the arrow keys, a search result). */
    public CsvViewer onCellSelect(Consumer<Cell> action) {
        return on("onCellSelect", v -> {
            if (v instanceof Map<?, ?> m) {
                action.accept(new Cell(whole(m.get("row")), whole(m.get("col")), m.get("value") == null ? "" : String.valueOf(m.get("value"))));
            }
        });
    }

    private static int whole(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }
}
