package io.joshuasalcedo.webview4j.devtools;

import io.joshuasalcedo.webview4j.NetworkEvent;
import io.joshuasalcedo.webview4j.WebSocketFrame;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.table.AbstractTableModel;

/**
 * The Network tab's requests: each fetch or XMLHttpRequest from its start to its answer or failure (one row, by
 * view and id), each other request the page made (pictures, scripts, styles), and each WebSocket with its frames.
 * Kept to the latest {@link #LIMIT} rows. Swing's thread only.
 */
final class NetworkModel extends AbstractTableModel {

    static final int LIMIT = 2_000;

    /** What a row is: a request still waiting, answered, failed, a WebSocket. */
    enum State { PENDING, DONE, FAILED, OPEN, CLOSED }

    /** One WebSocket frame: when, which way, what. */
    record Frame(Instant at, WebSocketFrame.Direction direction, String data) {}

    /** One request or socket, as the tab shows and details it. */
    static final class Row {
        final String view;
        final String key;
        String kind;
        String method;
        String url;
        long startedAt;
        int status;
        String statusText;
        long durationMs = -1;
        long size;
        String mimeType;
        String error;
        Map<String, String> requestHeaders = Map.of();
        String requestBody;
        Map<String, String> responseHeaders = Map.of();
        String responseBody;
        State state = State.PENDING;
        final List<Frame> frames = new ArrayList<>();

        Row(String view, String key) {
            this.view = view;
            this.key = key;
        }

        /** The last part of the URL, as a request list names it. */
        String name() {
            try {
                URI u = URI.create(url);
                String path = u.getRawPath() == null || u.getRawPath().isEmpty() ? "/" : u.getRawPath();
                String last = path.endsWith("/") && path.length() > 1 ? path : path.substring(path.lastIndexOf('/') + 1);
                if (last.isEmpty()) last = u.getHost() == null ? url : u.getHost();
                return u.getRawQuery() == null ? last : last + "?" + u.getRawQuery();
            } catch (RuntimeException e) {
                return url;
            }
        }

        /** fetch and XHR, websocket, or the page's other loads: the type filter's groups. */
        String group() {
            if ("websocket".equals(kind)) return "WebSocket";
            if ("fetch".equals(kind) || "xhr".equals(kind)) return "Fetch/XHR";
            return "Resources";
        }
    }

    static final String[] COLUMNS = {"Name", "Method", "Status", "Type", "Size", "Time", "Started"};

    private final Map<String, Row> byKey = new LinkedHashMap<>();
    private List<Row> shown = List.of();
    private String text = "";
    private String group = "All";

    /** A request's step: its row made at START (or at the first step that arrives), filled by the rest. */
    void accept(NetworkEvent e) {
        String key = e.view() + "#" + (e.phase() == NetworkEvent.Phase.RESOURCE ? "r" : "") + e.id();
        Row row = byKey.computeIfAbsent(key, k -> new Row(e.view(), k));
        switch (e.phase()) {
            case START, RESOURCE -> {
                row.kind = e.kind();
                row.method = e.method();
                row.url = e.url();
                row.startedAt = e.startedAt();
                row.requestHeaders = e.requestHeaders();
                row.requestBody = e.requestBody();
                if (e.phase() == NetworkEvent.Phase.RESOURCE) {
                    row.status = e.status();
                    row.durationMs = e.durationMs();
                    row.size = e.size();
                    row.state = e.status() >= 400 ? State.FAILED : State.DONE;
                }
            }
            case END -> {
                row.status = e.status();
                row.statusText = e.statusText();
                row.durationMs = e.durationMs();
                row.size = e.size();
                row.mimeType = e.mimeType();
                row.responseHeaders = e.responseHeaders();
                row.responseBody = e.responseBody();
                row.state = e.status() >= 400 || e.status() == 0 ? State.FAILED : State.DONE;
            }
            case ERROR -> {
                row.durationMs = e.durationMs();
                row.error = e.error();
                row.state = State.FAILED;
            }
        }
        changed();
    }

    /** A WebSocket's step: its row opened, its frames added, closed. */
    void accept(WebSocketFrame f) {
        String key = f.view() + "#ws" + f.socket();
        Row row = byKey.computeIfAbsent(key, k -> {
            Row r = new Row(f.view(), k);
            r.kind = "websocket";
            r.method = "GET";
            r.url = f.url();
            r.startedAt = System.currentTimeMillis();
            r.status = 101;
            r.state = State.OPEN;
            return r;
        });
        if (f.url() != null) row.url = f.url();
        switch (f.direction()) {
            case IN, OUT -> {
                row.frames.add(new Frame(Instant.now(), f.direction(), f.data()));
                if (f.data() != null) row.size += f.data().length();
            }
            case CLOSE -> {
                row.state = State.CLOSED;
                row.durationMs = System.currentTimeMillis() - row.startedAt;
            }
            case OPEN -> {}
        }
        changed();
    }

    void clear() {
        byKey.clear();
        changed();
    }

    /** Shows rows whose URL holds {@code text} (any case), of {@code group} ("All", "Fetch/XHR", "WebSocket", "Resources"). */
    void filter(String text, String group) {
        this.text = text == null ? "" : text.toLowerCase(Locale.ROOT);
        this.group = group == null ? "All" : group;
        changed();
    }

    Row row(int index) {
        return shown.get(index);
    }

    List<Row> rows() {
        return shown;
    }

    private void changed() {
        while (byKey.size() > LIMIT) byKey.remove(byKey.keySet().iterator().next());
        shown = byKey.values().stream()
                .filter(r -> r.url != null)
                .filter(r -> group.equals("All") || r.group().equals(group))
                .filter(r -> text.isEmpty() || r.url.toLowerCase(Locale.ROOT).contains(text))
                .toList();
        fireTableDataChanged();
    }

    @Override public int getRowCount() { return shown.size(); }
    @Override public int getColumnCount() { return COLUMNS.length; }
    @Override public String getColumnName(int column) { return COLUMNS[column]; }

    @Override
    public Object getValueAt(int index, int column) {
        Row r = shown.get(index);
        return switch (column) {
            case 0 -> r.name();
            case 1 -> r.method;
            case 2 -> status(r);
            case 3 -> r.kind;
            case 4 -> size(r.size);
            case 5 -> r.durationMs < 0 ? (r.state == State.OPEN ? "open" : "…") : r.durationMs + " ms";
            case 6 -> r.startedAt == 0 ? "" : java.time.LocalTime.ofInstant(Instant.ofEpochMilli(r.startedAt), java.time.ZoneId.systemDefault())
                    .withNano(0).toString();
            default -> "";
        };
    }

    static String status(Row r) {
        return switch (r.state) {
            case PENDING -> "(pending)";
            case FAILED -> r.error != null ? "(failed) " + r.error : r.status == 0 ? "(failed)" : Integer.toString(r.status);
            case OPEN -> "101 (open)";
            case CLOSED -> "101 (closed)";
            case DONE -> Integer.toString(r.status);
        };
    }

    static String size(long bytes) {
        if (bytes <= 0) return "";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.ROOT, "%.1f kB", bytes / 1024.0);
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024));
    }
}
