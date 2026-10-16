package io.joshuasalcedo.webview4j.devtools;

import io.joshuasalcedo.webview4j.LogRecord;
import io.joshuasalcedo.webview4j.WebViewEvent;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.table.AbstractTableModel;

/**
 * The log the dev window shows: the latest {@value #MOST} lines, newest last, from Java's own
 * logging, the native side, the pages' console and the pages' events. A filter keeps the lines
 * whose text has it. Changed only on Swing's thread.
 */
final class LogTableModel extends AbstractTableModel {

    /** The most lines kept: older ones go. */
    static final int MOST = 5_000;

    private static final String[] COLUMNS = {"Time", "Source", "Level", "View", "Message"};
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    /** Where a line came from: the app's Java, the native side, a page's console, a page's event. */
    enum Source { JAVA, NATIVE, PAGE, EVENT }

    /** One line. */
    record Entry(Instant at, Source source, String level, String view, String message) {

        /** A log record from the native side or a page's console. */
        static Entry of(LogRecord log, Instant at) {
            return new Entry(at, log.source() == LogRecord.Source.PAGE ? Source.PAGE : Source.NATIVE,
                    log.level().name(), log.view() == null ? "" : log.view(),
                    log.target() == null || log.target().isBlank() ? log.message() : log.target() + ": " + log.message());
        }

        /** Any other event: what it is and what it says. */
        static Entry of(WebViewEvent event, Instant at) {
            if (event instanceof LogRecord log) return of(log, at);
            String text = event.toString();
            String view = "";
            int found = text.indexOf("view=");
            if (found >= 0) {
                int end = text.indexOf(',', found);
                view = text.substring(found + 5, end < 0 ? text.indexOf(']', found) : end);
            }
            return new Entry(at, Source.EVENT, "INFO", view, text);
        }

        String time() {
            return LocalTime.ofInstant(at, ZoneId.systemDefault()).format(TIME);
        }

        boolean has(String text) {
            if (text.isBlank()) return true;
            String wanted = text.toLowerCase(Locale.ROOT);
            return message.toLowerCase(Locale.ROOT).contains(wanted)
                    || level.toLowerCase(Locale.ROOT).contains(wanted)
                    || source.name().toLowerCase(Locale.ROOT).contains(wanted)
                    || view.toLowerCase(Locale.ROOT).contains(wanted);
        }
    }

    private final List<Entry> all = new ArrayList<>();
    private final List<Entry> shown = new ArrayList<>();
    private String filter = "";

    void add(Entry entry) {
        all.add(entry);
        if (all.size() > MOST) {
            Entry dropped = all.removeFirst();
            if (!shown.isEmpty() && shown.getFirst() == dropped) {
                shown.removeFirst();
                fireTableRowsDeleted(0, 0);
            }
        }
        if (entry.has(filter)) {
            shown.add(entry);
            fireTableRowsInserted(shown.size() - 1, shown.size() - 1);
        }
    }

    void filter(String text) {
        filter = text == null ? "" : text.strip();
        shown.clear();
        for (Entry entry : all) if (entry.has(filter)) shown.add(entry);
        fireTableDataChanged();
    }

    void clear() {
        all.clear();
        shown.clear();
        fireTableDataChanged();
    }

    Entry entry(int row) {
        return shown.get(row);
    }

    /** Every line kept, the filter aside. */
    int total() {
        return all.size();
    }

    @Override public int getRowCount()                 { return shown.size(); }
    @Override public int getColumnCount()              { return COLUMNS.length; }
    @Override public String getColumnName(int column)  { return COLUMNS[column]; }

    @Override
    public Object getValueAt(int row, int column) {
        Entry entry = shown.get(row);
        return switch (column) {
            case 0 -> entry.time();
            case 1 -> entry.source().name();
            case 2 -> entry.level();
            case 3 -> entry.view();
            default -> entry.message();
        };
    }
}
