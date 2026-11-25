package io.joshuasalcedo.webview4j.devtools;

import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.Json;
import io.joshuasalcedo.webview4j.NetworkEvent;
import io.joshuasalcedo.webview4j.WebSocketFrame;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * The dev window's Network tab: the page's requests as a list (name, method, status, type, size, time), filtered
 * by URL and type, and the chosen one's headers, request and response bodies, timing, and a WebSocket's frames;
 * pause, clear, and the view's own developer tools. Fed by {@link NetworkEvent}s and {@link WebSocketFrame}s.
 */
final class NetworkPanel {

    private final App app;
    private final NetworkModel model = new NetworkModel();
    private final JTable table = new JTable(model);
    private final JTextArea headers = area();
    private final JTextArea request = area();
    private final JTextArea response = area();
    private final JTextArea timing = area();
    private final JTextArea frames = area();
    private final JLabel summary = new JLabel(" ");
    private final JPanel panel = new JPanel(new BorderLayout());
    private volatile boolean paused;

    NetworkPanel(App app) {
        this.app = app;
        JTextField filter = new JTextField(24);
        filter.setToolTipText("Filter by URL");
        JComboBox<String> type = new JComboBox<>(new String[] {"All", "Fetch/XHR", "WebSocket", "Resources"});
        Runnable refilter = () -> model.filter(filter.getText(), (String) type.getSelectedItem());
        filter.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { refilter.run(); }
            @Override public void removeUpdate(DocumentEvent e) { refilter.run(); }
            @Override public void changedUpdate(DocumentEvent e) { refilter.run(); }
        });
        type.addActionListener(e -> refilter.run());
        JCheckBox pause = new JCheckBox("Pause");
        pause.addActionListener(e -> paused = pause.isSelected());
        JButton clear = new JButton("Clear");
        clear.addActionListener(e -> {
            model.clear();
            show(null);
        });
        JButton devtools = new JButton("Open DevTools");
        devtools.setToolTipText("The view's own developer tools (Elements, Console, Network, Sources); the window needs WindowOptions.devtools(true)");
        devtools.addActionListener(e -> openDevTools());

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bar.add(new JLabel("Filter"));
        bar.add(filter);
        bar.add(type);
        bar.add(pause);
        bar.add(clear);
        bar.add(devtools);
        bar.add(summary);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoCreateRowSorter(true);
        table.getColumnModel().getColumn(0).setPreferredWidth(320);
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean selected, boolean focus, int row, int column) {
                Component c = super.getTableCellRendererComponent(t, value, selected, focus, row, column);
                NetworkModel.Row r = model.row(t.convertRowIndexToModel(row));
                if (!selected) c.setForeground(r.state == NetworkModel.State.FAILED ? new Color(0xc4, 0x2b, 0x1c) : t.getForeground());
                return c;
            }
        });
        table.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int at = table.getSelectedRow();
            show(at < 0 ? null : model.row(table.convertRowIndexToModel(at)));
        });

        JTabbedPane details = new JTabbedPane();
        details.addTab("Headers", new JScrollPane(headers));
        details.addTab("Request", new JScrollPane(request));
        details.addTab("Response", new JScrollPane(response));
        details.addTab("Timing", new JScrollPane(timing));
        details.addTab("Frames", new JScrollPane(frames));
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(table), details);
        split.setResizeWeight(0.6);
        panel.add(bar, BorderLayout.NORTH);
        panel.add(split, BorderLayout.CENTER);
    }

    JPanel component() {
        return panel;
    }

    NetworkModel model() {
        return model;
    }

    /** From any thread: a request's step. */
    void accept(NetworkEvent e) {
        if (paused) return;
        SwingUtilities.invokeLater(() -> {
            model.accept(e);
            summarize();
        });
    }

    /** From any thread: a WebSocket's step. */
    void accept(WebSocketFrame f) {
        if (paused) return;
        SwingUtilities.invokeLater(() -> {
            model.accept(f);
            summarize();
        });
    }

    private void summarize() {
        long bytes = model.rows().stream().mapToLong(r -> r.size).sum();
        summary.setText(model.rows().size() + " requests · " + NetworkModel.size(bytes));
    }

    private void openDevTools() {
        int at = table.getSelectedRow();
        String view = at >= 0 ? model.row(table.convertRowIndexToModel(at)).view : null;
        app.views().thenAccept(views -> {
            String id = view != null ? view : views.isEmpty() ? null : views.getFirst().id();
            if (id == null) return;
            app.view(id).openDevTools().exceptionally(e -> {
                SwingUtilities.invokeLater(() -> summary.setText(id + ": " + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage())));
                return null;
            });
        });
    }

    private void show(NetworkModel.Row r) {
        if (r == null) {
            for (JTextArea a : new JTextArea[] {headers, request, response, timing, frames}) a.setText("");
            return;
        }
        StringBuilder h = new StringBuilder();
        h.append("General\n  URL: ").append(r.url).append("\n  Method: ").append(r.method)
                .append("\n  Status: ").append(NetworkModel.status(r)).append(r.statusText == null || r.statusText.isEmpty() ? "" : " " + r.statusText)
                .append("\n  Type: ").append(r.kind).append(r.mimeType == null ? "" : " (" + r.mimeType + ")")
                .append("\n  View: ").append(r.view).append("\n");
        h.append("\nResponse headers\n").append(lines(r.responseHeaders));
        h.append("\nRequest headers\n").append(lines(r.requestHeaders));
        headers.setText(h.toString());
        request.setText(pretty(r.requestBody));
        response.setText(r.error != null ? "Failed: " + r.error : pretty(r.responseBody));
        timing.setText("Started: " + (r.startedAt == 0 ? "" : java.time.Instant.ofEpochMilli(r.startedAt))
                + "\nTook: " + (r.durationMs < 0 ? "still waiting" : r.durationMs + " ms")
                + "\nSize: " + NetworkModel.size(r.size) + (r.size > 0 ? " (" + r.size + " bytes)" : ""));
        StringBuilder f = new StringBuilder();
        for (NetworkModel.Frame frame : r.frames) {
            f.append(java.time.LocalTime.ofInstant(frame.at(), java.time.ZoneId.systemDefault()).withNano(0))
                    .append(frame.direction() == WebSocketFrame.Direction.IN ? "  ↓ " : "  ↑ ").append(frame.data()).append('\n');
        }
        frames.setText(f.toString());
        for (JTextArea a : new JTextArea[] {headers, request, response, timing, frames}) a.setCaretPosition(0);
    }

    private static String lines(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) return "  (none reported)\n";
        StringBuilder out = new StringBuilder();
        headers.forEach((k, v) -> out.append("  ").append(k).append(": ").append(v).append('\n'));
        return out.toString();
    }

    /** JSON laid out, anything else as it came. */
    static String pretty(String body) {
        if (body == null) return "";
        String t = body.strip();
        if (!(t.startsWith("{") || t.startsWith("["))) return body;
        try {
            return indent(Json.parse(t), 0);
        } catch (RuntimeException e) {
            return body;
        }
    }

    private static String indent(Object value, int depth) {
        String pad = "  ".repeat(depth + 1);
        String end = "  ".repeat(depth);
        if (value instanceof Map<?, ?> map) {
            if (map.isEmpty()) return "{}";
            StringBuilder out = new StringBuilder("{\n");
            int i = 0;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                out.append(pad).append(Json.write(String.valueOf(e.getKey()))).append(": ").append(indent(e.getValue(), depth + 1));
                out.append(++i < map.size() ? ",\n" : "\n");
            }
            return out.append(end).append('}').toString();
        }
        if (value instanceof java.util.List<?> list) {
            if (list.isEmpty()) return "[]";
            StringBuilder out = new StringBuilder("[\n");
            for (int i = 0; i < list.size(); i++) {
                out.append(pad).append(indent(list.get(i), depth + 1)).append(i + 1 < list.size() ? ",\n" : "\n");
            }
            return out.append(end).append(']').toString();
        }
        return Json.write(value);
    }

    private static JTextArea area() {
        JTextArea a = new JTextArea();
        a.setEditable(false);
        a.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        return a;
    }
}
