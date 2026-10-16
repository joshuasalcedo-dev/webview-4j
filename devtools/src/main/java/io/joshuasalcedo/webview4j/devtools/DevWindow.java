package io.joshuasalcedo.webview4j.devtools;

import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.Metrics;
import io.joshuasalcedo.webview4j.Subscription;
import io.joshuasalcedo.webview4j.ViewInfo;
import io.joshuasalcedo.webview4j.WebViewEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.lang.reflect.InvocationTargetException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

/**
 * A Swing window to keep beside an app while developing it. At the top, what the app does now:
 * frames per second each way between Java and the native side, how long the latest action took and
 * the mean and 95th percentile, the same for the pages' calls, and each page's frames per second.
 * Below, the log (Java's, the native side's, the pages' console and events, filterable), the
 * timings per action, and the open pages.
 * <pre>{@code
 * try (App app = App.start()) {
 *     DevWindow.attachIfDev(app);          // only with -Dwebview4j.dev=true
 *     ...
 * }
 * }</pre>
 *
 * <p>It only reads: {@link App#metrics()}, the app's events, Java's logging. To measure a page's
 * frames per second it evaluates a small, fixed script in each page once a second, which counts
 * {@code requestAnimationFrame} callbacks; those evaluations show among the actions as
 * {@code view.eval}.
 */
public final class DevWindow implements AutoCloseable {

    /** The system property, and the environment variable, that {@link #attachIfDev} looks for. */
    public static final String PROPERTY = "webview4j.dev";

    /**
     * Counts the page's animation frames, once per page, and answers the latest second's count.
     * Fixed: nothing is put into it.
     */
    static final String FPS_PROBE = """
            (() => {
              const w = window;
              if (!w.__webview4jFps) {
                const fps = w.__webview4jFps = { frames: 0, value: 0, since: performance.now() };
                const tick = (now) => {
                  fps.frames++;
                  if (now - fps.since >= 1000) {
                    fps.value = Math.round(fps.frames * 1000 / (now - fps.since));
                    fps.frames = 0;
                    fps.since = now;
                  }
                  requestAnimationFrame(tick);
                };
                requestAnimationFrame(tick);
              }
              return w.__webview4jFps.value;
            })()""";

    private static final Logger ROOT = Logger.getLogger("");

    private final App app;
    private final JFrame frame = new JFrame("webview-4j dev");
    private final LogTableModel log = new LogTableModel();
    private final Map<String, JLabel> values = new LinkedHashMap<>();
    private final DefaultTableModel actions = new DefaultTableModel(
            new String[] {"Action", "Count", "Last ms", "Mean ms", "p95 ms", "Slowest ms"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final DefaultTableModel pages = new DefaultTableModel(new String[] {"View", "Title", "URL", "Frames/s"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    /** Readings of the latest second or so, for rates over a second rather than one tick. */
    private final Deque<Metrics> readings = new ArrayDeque<>();
    private final Map<String, Integer> pageFps = new LinkedHashMap<>();
    private final Subscription events;
    private final Handler javaLog;
    private final Timer ticks;
    private final Thread fpsPoller;
    private volatile boolean paused;
    private volatile boolean closed;

    private DevWindow(App app) {
        this.app = app;
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosed(java.awt.event.WindowEvent e) { close(); }
        });
        frame.setLayout(new BorderLayout());
        frame.add(metricsPanel(), BorderLayout.NORTH);
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Log", logPanel());
        tabs.addTab("Actions", new JScrollPane(numbers(new JTable(actions))));
        tabs.addTab("Pages", new JScrollPane(numbers(new JTable(pages))));
        frame.add(tabs, BorderLayout.CENTER);
        frame.setSize(new Dimension(1000, 640));
        frame.setLocationByPlatform(true);

        events = app.subscribe(WebViewEvent.class, event -> {
            if (paused) return;
            var entry = LogTableModel.Entry.of(event, Instant.now());
            SwingUtilities.invokeLater(() -> log.add(entry));
        });
        javaLog = new Handler() {
            @Override
            public void publish(java.util.logging.LogRecord record) {
                if (paused || !isLoggable(record)) return;
                String message = record.getMessage();
                if (record.getParameters() != null && message != null) {
                    message = java.text.MessageFormat.format(message, record.getParameters());
                }
                if (record.getThrown() != null) message += " — " + record.getThrown();
                var entry = new LogTableModel.Entry(record.getInstant(), LogTableModel.Source.JAVA,
                        level(record.getLevel()), "", record.getLoggerName() + ": " + message);
                SwingUtilities.invokeLater(() -> log.add(entry));
            }
            @Override public void flush() {}
            @Override public void close() {}
        };
        javaLog.setLevel(Level.ALL);
        ROOT.addHandler(javaLog);

        ticks = new Timer(500, e -> tick());
        ticks.start();
        fpsPoller = Thread.ofVirtual().name("webview4j-dev-fps").start(this::pollFps);
        app.whenStopped().thenRun(() -> SwingUtilities.invokeLater(() -> frame.setTitle("webview-4j dev (stopped)")));
        frame.setVisible(true);
    }

    /** Opens the dev window beside the app, on Swing's thread; closing it leaves the app running. */
    public static DevWindow attach(App app) {
        Objects.requireNonNull(app, "app");
        if (SwingUtilities.isEventDispatchThread()) return new DevWindow(app);
        DevWindow[] made = new DevWindow[1];
        try {
            SwingUtilities.invokeAndWait(() -> made[0] = new DevWindow(app));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while opening the dev window", e);
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("the dev window did not open", e.getCause());
        }
        return made[0];
    }

    /**
     * Opens the dev window only while developing: when the system property {@value #PROPERTY} or
     * the environment variable {@code WEBVIEW4J_DEV} is {@code true}, and there is a screen.
     */
    public static DevWindow attachIfDev(App app) {
        if (!wanted(System.getProperty(PROPERTY), System.getenv("WEBVIEW4J_DEV"))
                || java.awt.GraphicsEnvironment.isHeadless()) {
            return null;
        }
        return attach(app);
    }

    static boolean wanted(String property, String environment) {
        return "true".equalsIgnoreCase(property) || (property == null && "true".equalsIgnoreCase(environment));
    }

    /** Closes the window and stops reading the app. */
    @Override
    public void close() {
        if (closed) return;
        closed = true;
        events.close();
        ROOT.removeHandler(javaLog);
        fpsPoller.interrupt();
        SwingUtilities.invokeLater(() -> {
            ticks.stop();
            frame.dispose();
        });
    }

    // ---- the window ----

    private JPanel metricsPanel() {
        JPanel panel = new JPanel(new GridLayout(2, 6, 12, 4));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        for (String name : List.of("Uptime", "Frames/s in", "Frames/s out", "KB/s in · out",
                "Actions/s", "Page calls/s",
                "Action last ms", "Action mean · p95 ms", "Page call last ms", "Page call mean · p95 ms",
                "Page frames/s", "Frames in · out")) {
            JPanel cell = new JPanel(new BorderLayout());
            JLabel label = new JLabel(name);
            label.setFont(label.getFont().deriveFont(Font.PLAIN, 11f));
            label.setForeground(Color.GRAY);
            JLabel value = new JLabel("–");
            value.setFont(value.getFont().deriveFont(Font.BOLD, 15f));
            value.setName(name);
            values.put(name, value);
            cell.add(label, BorderLayout.NORTH);
            cell.add(value, BorderLayout.CENTER);
            panel.add(cell);
        }
        return panel;
    }

    private JPanel logPanel() {
        JTable table = new JTable(log);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        int[] widths = {90, 60, 55, 60};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
            table.getColumnModel().getColumn(i).setMaxWidth(widths[i] * 2);
        }
        table.getColumnModel().getColumn(4).setPreferredWidth(700);
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean selected, boolean focus, int row, int column) {
                Component cell = super.getTableCellRendererComponent(t, value, selected, focus, row, column);
                if (!selected) {
                    String level = log.entry(row).level();
                    cell.setForeground(switch (level) {
                        case "ERROR", "SEVERE" -> new Color(0xC0392B);
                        case "WARN", "WARNING" -> new Color(0xB9770E);
                        case "DEBUG", "TRACE", "FINE", "FINER", "FINEST" -> Color.GRAY;
                        default -> t.getForeground();
                    });
                }
                return cell;
            }
        });
        JScrollPane scroll = new JScrollPane(table);
        JCheckBox follow = new JCheckBox("Follow", true);
        log.addTableModelListener(e -> {
            if (follow.isSelected() && log.getRowCount() > 0) {
                SwingUtilities.invokeLater(() -> table.scrollRectToVisible(table.getCellRect(log.getRowCount() - 1, 0, true)));
            }
        });

        JTextField filter = new JTextField(24);
        filter.setToolTipText("Keep the lines that have this text");
        filter.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e)  { log.filter(filter.getText()); }
            @Override public void removeUpdate(DocumentEvent e)  { log.filter(filter.getText()); }
            @Override public void changedUpdate(DocumentEvent e) { log.filter(filter.getText()); }
        });
        JCheckBox pause = new JCheckBox("Pause");
        pause.addActionListener(e -> paused = pause.isSelected());
        JButton clear = new JButton("Clear");
        clear.addActionListener(e -> log.clear());

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bar.add(new JLabel("Filter"));
        bar.add(filter);
        bar.add(follow);
        bar.add(pause);
        bar.add(clear);
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(bar, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    private static JTable numbers(JTable table) {
        DefaultTableCellRenderer right = new DefaultTableCellRenderer();
        right.setHorizontalAlignment(JLabel.RIGHT);
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean selected, boolean focus, int row, int column) {
                Component cell = super.getTableCellRendererComponent(t, value, selected, focus, row, column);
                setHorizontalAlignment(value instanceof Number || (value instanceof String s && s.matches("[0-9.,–]+")) ? RIGHT : LEFT);
                return cell;
            }
        });
        return table;
    }

    // ---- reading the app ----

    private void tick() {
        Metrics now = app.metrics();
        readings.addLast(now);
        // Rates over the latest second: the oldest reading kept is the latest one a second old or more.
        while (readings.size() > 2) {
            var iterator = readings.iterator();
            iterator.next();
            if (now.uptime().minus(iterator.next().uptime()).toMillis() < 1000) break;
            readings.removeFirst();
        }
        Rates rates = Rates.between(readings.peekFirst(), now);

        set("Uptime", uptime(now.uptime()));
        set("Frames/s in", whole(rates.framesInPerSecond()));
        set("Frames/s out", whole(rates.framesOutPerSecond()));
        set("KB/s in · out", one(rates.kilobytesInPerSecond()) + " · " + one(rates.kilobytesOutPerSecond()));
        set("Actions/s", whole(rates.actionsPerSecond()));
        set("Page calls/s", whole(rates.pageCallsPerSecond()));
        set("Action last ms", now.actions().count() == 0 ? "–" : ms(now.actions().lastMillis()));
        set("Action mean · p95 ms", now.actions().count() == 0 ? "–" : ms(now.actions().averageMillis()) + " · " + ms(now.actions().p95Millis()));
        set("Page call last ms", now.pageCalls().count() == 0 ? "–" : ms(now.pageCalls().lastMillis()));
        set("Page call mean · p95 ms", now.pageCalls().count() == 0 ? "–" : ms(now.pageCalls().averageMillis()) + " · " + ms(now.pageCalls().p95Millis()));
        set("Frames in · out", now.framesIn() + " · " + now.framesOut());
        synchronized (pageFps) {
            set("Page frames/s", pageFps.isEmpty() ? "–" : String.join(" · ", pageFps.values().stream().map(String::valueOf).toList()));
        }

        actions.setRowCount(0);
        for (Metrics.Timing timing : now.byAction()) {
            actions.addRow(new Object[] {timing.name(), timing.count(), ms(timing.lastMillis()), ms(timing.averageMillis()),
                    ms(timing.p95Millis()), ms(timing.maxMillis())});
        }
    }

    /** Once a second: the open pages, and each one's frames per second from the probe. */
    private void pollFps() {
        while (!closed) {
            try {
                List<ViewInfo> views = app.views().get(5, TimeUnit.SECONDS);
                Map<String, Integer> measured = new LinkedHashMap<>();
                for (ViewInfo view : views) {
                    try {
                        Object fps = app.view(view.id()).eval(FPS_PROBE).get(5, TimeUnit.SECONDS);
                        measured.put(view.id(), fps instanceof Number n ? n.intValue() : 0);
                    } catch (Exception e) {
                        measured.put(view.id(), 0);
                    }
                }
                synchronized (pageFps) {
                    pageFps.clear();
                    pageFps.putAll(measured);
                }
                SwingUtilities.invokeLater(() -> {
                    pages.setRowCount(0);
                    for (ViewInfo view : views) {
                        pages.addRow(new Object[] {view.id(), view.title(), view.url(), measured.getOrDefault(view.id(), 0)});
                    }
                });
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                return;
            } catch (Exception e) {
                // The app is stopping, or busy: try again in a second.
                if (app.whenStopped().isDone()) return;
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException stop) {
                    return;
                }
            }
        }
    }

    private void set(String name, String value) {
        values.get(name).setText(value);
    }

    static String uptime(Duration uptime) {
        long seconds = uptime.toSeconds();
        return seconds < 3600
                ? String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
                : String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }

    static String ms(double millis) {
        return millis >= 100 ? String.format(Locale.ROOT, "%.0f", millis)
                : millis >= 10 ? String.format(Locale.ROOT, "%.1f", millis)
                : String.format(Locale.ROOT, "%.2f", millis);
    }

    private static String whole(double value) {
        return String.format(Locale.ROOT, "%.0f", value);
    }

    private static String one(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String level(Level level) {
        int value = level.intValue();
        if (value >= Level.SEVERE.intValue()) return "ERROR";
        if (value >= Level.WARNING.intValue()) return "WARN";
        if (value >= Level.INFO.intValue()) return "INFO";
        if (value >= Level.FINE.intValue()) return "DEBUG";
        return "TRACE";
    }
}
