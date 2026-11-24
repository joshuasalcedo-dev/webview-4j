package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** A log that grows as things happen: time, level, source, message; a line opens to show its details; it follows the newest line unless scrolled up. */
public final class LogView extends Component<LogView> {

    public LogView(String label, java.util.List<LogLine> lines) {
        super("logView");
        prop("label", label);
        prop("lines", rows(lines));
    }

    public LogView empty(String text) { return prop("empty", text); }
    /** Keep the newest line in view (true unless set). */
    public LogView follow(boolean follow) { return prop("follow", follow); }

    /** One line: when, how much it matters, what happened, from where, and its technical details. */
    public record LogLine(java.time.Instant time, LogLevel level, String message, String source, String detail) {
        public LogLine(java.time.Instant time, LogLevel level, String message) {
            this(time, level, message, null, null);
        }
    }

    private static java.util.List<java.util.Map<String, Object>> rows(java.util.List<LogLine> lines) {
        java.util.List<java.util.Map<String, Object>> rows = new java.util.ArrayList<>();
        int id = 0;
        for (LogLine l : lines) {
            java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("id", id++);
            row.put("time", l.time().toEpochMilli());
            row.put("level", l.level().key());
            row.put("message", l.message());
            if (l.source() != null) row.put("source", l.source());
            if (l.detail() != null) row.put("detail", l.detail());
            rows.add(row);
        }
        return rows;
    }
}
