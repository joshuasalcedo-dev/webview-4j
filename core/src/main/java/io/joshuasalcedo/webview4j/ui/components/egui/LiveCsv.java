package io.joshuasalcedo.webview4j.ui.components.egui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Rows that keep coming (an import, a log), shown as they come and scrolled to the newest: egui-4j's
 * {@code live-csv}, egui-spreadsheet's viewer (sort, filter, search, copy) drawn by Rust in the page. Java keeps the
 * rows, at most {@link #maxRows} of them.
 * <pre>{@code
 * LiveCsv imported = new LiveCsv("Customers imported").columns("id", "name", "country").height(320);
 * worker: imported.addRow(42, "Ana Reyes", "PH");         // from any thread
 * done:   imported.done();      failed: imported.failed("line 812: not a number");
 * }</pre>
 */
public final class LiveCsv extends EguiComponent<LiveCsv> {

    private final List<List<Object>> rows = new ArrayList<>();
    private int maxRows = 10_000;

    public LiveCsv(String title) {
        super("live-csv", title);
        eguiProp("name", title);
    }

    /** The columns' names. */
    public LiveCsv columns(String... names) {
        for (String n : names) Objects.requireNonNull(n, "a column's name");
        return eguiProp("columns", List.of(names));
    }

    /** Adds a row at the end: numbers, text, true/false, or null for an empty cell. From any thread. */
    public LiveCsv addRow(Object... cells) {
        List<Object> row = new ArrayList<>(cells.length);
        for (Object c : cells) {
            if (c != null && !(c instanceof Number || c instanceof CharSequence || c instanceof Boolean)) {
                throw new IllegalArgumentException("a cell is a number, text, true/false or null, not " + c.getClass().getSimpleName());
            }
            row.add(c instanceof CharSequence s ? s.toString() : c);
        }
        synchronized (rows) {
            rows.add(row);
            if (rows.size() > maxRows) rows.subList(0, rows.size() - maxRows).clear();
        }
        return send();
    }

    /** Takes every row away. */
    public LiveCsv clear() {
        synchronized (rows) {
            rows.clear();
        }
        return send();
    }

    /** How many rows it has now. */
    public int getRowCount() {
        synchronized (rows) {
            return rows.size();
        }
    }

    /** It keeps only the newest {@code max} rows (10,000 at first). */
    public LiveCsv maxRows(int max) {
        if (max < 1) throw new IllegalArgumentException("maxRows is at least 1: " + max);
        synchronized (rows) {
            maxRows = max;
            if (rows.size() > max) rows.subList(0, rows.size() - max).clear();
        }
        return send();
    }

    /** Keeps the newest rows in view (on at first). */
    public LiveCsv follow(boolean follow) { return eguiProp("follow", follow); }

    /** Still coming, this far from 0 to 1. */
    public LiveCsv progress(double fraction) {
        if (!(fraction >= 0 && fraction <= 1)) throw new IllegalArgumentException("progress is 0 to 1: " + fraction);
        eguiProp("failed", null);
        eguiProp("done", null);
        return eguiProp("progress", fraction);
    }

    /** All of it is there. */
    public LiveCsv done() {
        eguiProp("progress", null);
        return eguiProp("done", true);
    }

    /** It stopped: why. */
    public LiveCsv failed(String why) {
        eguiProp("progress", null);
        return eguiProp("failed", Objects.requireNonNull(why, "why"));
    }

    /** The rows as the Rust viewer reads them: cleared, then all of them again, so a redraw never doubles them. */
    private LiveCsv send() {
        List<List<Object>> copy;
        synchronized (rows) {
            copy = new ArrayList<>(rows.size());
            for (List<Object> r : rows) copy.add(Arrays.asList(r.toArray()));
        }
        eguiProp("clear", true);
        return eguiProp("append", copy);
    }
}
