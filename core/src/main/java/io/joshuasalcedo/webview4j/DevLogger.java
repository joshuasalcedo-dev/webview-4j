package io.joshuasalcedo.webview4j;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The dev logger: a window beside an app while it is developed, showing what the app does, in one list: the log
 * (Java's, the native side's, the pages' console), every call a page makes (fetch and XHR, with bodies, headers,
 * status and time), every call a page makes to Java (commands and routes, with what was sent and answered), with
 * statistics, groups and charts. It is {@code @webview4j/ui}'s {@code DevLogger} (egui-4j's {@code dev-logger}),
 * fed from here. The Spring Boot starter opens it in development.
 * <pre>{@code
 * DevLogger logger = DevLogger.open(app).join();
 * logger.watch(mainView);   // its fetch and XHR calls too
 * }</pre>
 * Its own window is left out of what it shows. Closing it (or {@link #close()}) stops it.
 *
 * <p>Its window holds only the latest {@value #SHOWN} entries; every entry goes to a file of this run ({@link #file()},
 * emptied when it opens, deleted when the app stops), and its search box searches all of that.
 */
public final class DevLogger implements AutoCloseable {

    private static final System.Logger LOG = System.getLogger(DevLogger.class.getName());

    /** The topic its page listens on for entries (an array each time): {@code @webview4j/ui}'s DEV_LOGGER_TOPIC. */
    public static final String TOPIC = "webview4j.devlogger.append";
    /** How often what came in is sent to the page, together. */
    static final long BATCH_MILLIS = 100;
    /** At most this many entries wait for the page (the oldest go first): a flood does not grow without end. */
    static final int MAX_WAITING = 5000;
    /** Bodies longer than this are cut (in the window and in the file). */
    static final int MAX_BODY = 64 * 1024;
    /** How many entries the window holds; the rest are in the file, found by searching. */
    public static final int SHOWN = 100;
    /** The command its page searches the file with. */
    static final String SEARCH = "webview4j.devlogger.search";
    /** At most this many matches go back to the page (the latest). */
    static final int MAX_FOUND = 500;

    static final String PAGE = """
            <!doctype html>
            <meta charset="utf-8">
            <title>Dev logger</title>
            <link rel="stylesheet" href="%1$sui.css">
            <style>html, body, #root { height: 100%%; margin: 0; }</style>
            <div id="root"></div>
            <script type="module">
              import { mountDevLogger } from "%1$sui.js";
              mountDevLogger(document.getElementById("root"));
            </script>
            """.formatted(App.UI_URL);

    private static final Logger ROOT = Logger.getLogger("");

    private final App app;
    private final View view;
    private final ConcurrentLinkedQueue<Map<String, Object>> waiting = new ConcurrentLinkedQueue<>();
    private final List<Subscription> subscriptions = new ArrayList<>();
    private final Handler javaLog;
    private final ScheduledExecutorService sender;
    private final Path file;
    private final BufferedWriter out;
    private volatile boolean closed;

    private DevLogger(App app, View view, Path file) {
        this.app = app;
        this.view = view;
        this.file = file;
        try {
            Files.createDirectories(file.getParent());
            // a new run, a new log: what an earlier run wrote is gone
            out = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("the dev logger's file " + file, e);
        }
        file.toFile().deleteOnExit();
        subscriptions.add(app.handle(SEARCH, call -> search(call.text("text"))));
        String own = view.id();
        // what happens in the logger's own window is not the app's (and would feed itself)
        subscriptions.add(app.subscribe(LogRecord.class, r -> { if (!own.equals(r.view())) add(entry(r)); }));
        subscriptions.add(app.subscribe(ApiCall.class, c -> { if (!own.equals(c.view())) add(entry(c)); }));
        subscriptions.add(app.subscribe(PageCall.class, c -> { if (!own.equals(c.view())) add(entry(c)); }));
        subscriptions.add(app.subscribe(Closed.class, c -> { if (own.equals(c.view())) close(); }));
        javaLog = new Handler() {
            @Override public void publish(java.util.logging.LogRecord record) {
                if (isLoggable(record) && !record.getLoggerName().startsWith(DevLogger.class.getName())) add(entry(record));
            }
            @Override public void flush() {}
            @Override public void close() {}
        };
        javaLog.setLevel(Level.ALL);
        ROOT.addHandler(javaLog);
        sender = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("webview4j-dev-logger").daemon().factory());
        sender.scheduleWithFixedDelay(this::send, BATCH_MILLIS, BATCH_MILLIS, TimeUnit.MILLISECONDS);
        app.whenStopped().thenRun(this::close);
    }

    /** Opens the dev logger in a window of its own. */
    public static CompletableFuture<DevLogger> open(App app) {
        return open(app, options -> options);
    }

    /** Opens the dev logger in a window of its own, with options of yours (e.g. its size or place). */
    public static CompletableFuture<DevLogger> open(App app, UnaryOperator<WindowOptions> options) {
        Objects.requireNonNull(app, "app");
        WindowOptions page = Objects.requireNonNull(options, "options").apply(WindowOptions.html(PAGE).title("Dev logger").size(1100, 700));
        Path file = Path.of(System.getProperty("java.io.tmpdir"), "webview4j", "dev-logger-" + ProcessHandle.current().pid() + ".ndjson");
        return app.open(page).thenApply(view -> new DevLogger(app, view, file));
    }

    /** Where this run's entries are, one JSON object a line (emptied when the logger opens). */
    public Path file() {
        return file;
    }

    /**
     * The entries whose text contains {@code text} (any field, ignoring case), from the whole run: the latest
     * {@value #MAX_FOUND}, oldest first. What the page's search box asks for.
     */
    public List<Object> search(String text) {
        String needle = text == null ? "" : text.trim().toLowerCase();
        if (needle.isEmpty()) return List.of();
        java.util.ArrayDeque<String> found = new java.util.ArrayDeque<>();
        synchronized (out) {
            try {
                out.flush();
            } catch (IOException ignored) {
                // what could not be written cannot be found either
            }
        }
        try (var lines = Files.lines(file, StandardCharsets.UTF_8)) {
            lines.filter(line -> line.toLowerCase().contains(needle)).forEach(line -> {
                found.add(line);
                if (found.size() > MAX_FOUND) found.poll();
            });
        } catch (IOException | UncheckedIOException e) {
            LOG.log(System.Logger.Level.WARNING, "the dev logger could not search " + file, e);
            return List.of();
        }
        return found.stream().map(Json::parse).toList();
    }

    /** The logger's window. */
    public View view() {
        return view;
    }

    /** Shows the fetch and XHR calls of {@code page} too (its console is shown already). */
    public CompletableFuture<Void> watch(View page) {
        return page.capture(Capture.API, true);
    }

    /**
     * Whether Java's own logging ({@code java.util.logging}, where {@code System.Logger} goes) is shown: on at first.
     * Off when the app's logging framework is shown another way (the Spring Boot starter's Logback appender, which
     * gets {@code java.util.logging} too), so no line shows twice.
     */
    public void javaLogging(boolean on) {
        ROOT.removeHandler(javaLog);
        if (on && !closed) ROOT.addHandler(javaLog);
    }

    /** Adds an entry of your own, in the dev logger's format (kind log, http, bridge or perf; see the kit's DevLogEntry). */
    public void add(Map<String, Object> entry) {
        if (closed) return;
        waiting.add(entry);
        while (waiting.size() > MAX_WAITING) waiting.poll();
    }

    private void send() {
        if (closed || waiting.isEmpty()) return;
        List<Map<String, Object>> batch = new ArrayList<>();
        for (Map<String, Object> e; (e = waiting.poll()) != null; ) batch.add(e);
        // all of it to the file, where search finds it (the window keeps only the latest)
        synchronized (out) {
            try {
                for (Map<String, Object> e : batch) {
                    out.write(Json.write(e));
                    out.newLine();
                }
                out.flush();
            } catch (IOException e) {
                LOG.log(System.Logger.Level.WARNING, "the dev logger could not write to " + file, e);
            }
        }
        view.emit(TOPIC, batch).exceptionally(error -> {
            LOG.log(System.Logger.Level.DEBUG, "the dev logger's page did not take " + batch.size() + " entries", error);
            return null;
        });
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        subscriptions.forEach(Subscription::close);
        ROOT.removeHandler(javaLog);
        sender.shutdownNow();
        synchronized (out) {
            try {
                out.close();
            } catch (IOException ignored) {
                // closing anyway
            }
        }
        view.close();
    }

    // ---- the entries: the dev logger's format (crates/egui-4j/src/dev_logger.rs) ----

    /**
     * A log line from a logging framework of the app's (e.g. Logback): its time (ms since 1970), level ("error",
     * "warn", "info", "debug", "trace"), logger and message.
     */
    public static Map<String, Object> log(long time, String level, String logger, String message) {
        Map<String, Object> e = base("log", time);
        e.put("level", level == null ? "info" : level.toLowerCase());
        e.put("target", logger == null ? "" : logger);
        e.put("message", message == null ? "" : message);
        return e;
    }

    /**
     * A call Java made (an HTTP client's request and its answer). {@code status} 0, or an {@code error}, when it got
     * no answer. Bodies: text (cut when long), or null when not read (a stream, a file).
     */
    public static Map<String, Object> javaCall(long time, String method, String url, int status, double durationMs,
            Map<String, ?> requestHeaders, String requestBody, Map<String, ?> responseHeaders, String responseBody,
            String error) {
        Map<String, Object> e = base("http", time);
        e.put("source", "java");
        e.put("method", method == null ? "GET" : method);
        e.put("url", url == null ? "" : url);
        if (status > 0) e.put("status", status);
        if (error != null) e.put("error", error);
        else if (status <= 0) e.put("error", "no answer");
        e.put("durationMs", durationMs);
        e.put("request", payload(requestHeaders, requestBody));
        e.put("response", payload(responseHeaders, responseBody));
        return e;
    }

    static Map<String, Object> entry(LogRecord record) {
        Map<String, Object> e = base("log", System.currentTimeMillis());
        e.put("level", record.level().name().toLowerCase());
        String target = record.source() == LogRecord.Source.PAGE ? "page" : record.target();
        e.put("target", target == null ? "" : target);
        e.put("message", record.message() == null ? "" : record.message());
        return e;
    }

    static Map<String, Object> entry(java.util.logging.LogRecord record) {
        Map<String, Object> e = base("log", record.getMillis());
        e.put("level", level(record.getLevel()));
        e.put("target", record.getLoggerName() == null ? "java" : record.getLoggerName());
        String message = record.getMessage() == null ? "" : record.getMessage();
        if (record.getParameters() != null && record.getParameters().length > 0) {
            try {
                message = java.text.MessageFormat.format(message, record.getParameters());
            } catch (IllegalArgumentException ignored) {
                // not a pattern: as it is
            }
        }
        if (record.getThrown() != null) message += " (" + record.getThrown() + ")";
        e.put("message", message);
        return e;
    }

    static Map<String, Object> entry(ApiCall call) {
        Map<String, Object> e = base("http", System.currentTimeMillis());
        e.put("source", "page");
        e.put("method", call.method() == null ? "GET" : call.method());
        e.put("url", call.url() == null ? "" : call.url());
        if (call.status() > 0) e.put("status", call.status());
        else e.put("error", "no answer");
        e.put("durationMs", (double) call.durationMs());
        e.put("request", payload(call.data().get("requestHeaders"), call.requestBody()));
        e.put("response", payload(call.data().get("responseHeaders"), call.responseBody()));
        return e;
    }

    static Map<String, Object> entry(PageCall call) {
        Map<String, Object> e = base("bridge", System.currentTimeMillis());
        e.put("direction", "toJava");
        e.put("name", call.name());
        e.put("durationMs", call.durationMs());
        e.put("request", payload(null, call.args()));
        if (call.error() != null) e.put("error", call.error());
        else e.put("response", payload(null, call.result()));
        return e;
    }

    private static Map<String, Object> base(String kind, long time) {
        Map<String, Object> e = new LinkedHashMap<>();
        e.put("kind", kind);
        e.put("time", time);
        return e;
    }

    /** Headers (a map of text) and a body (text cut short, or JSON values: anything else is written as JSON). */
    private static Map<String, Object> payload(Object headers, Object body) {
        Map<String, Object> p = new LinkedHashMap<>();
        Map<String, Object> h = new LinkedHashMap<>();
        if (headers instanceof Map<?, ?> map) map.forEach((k, v) -> h.put(String.valueOf(k), v == null ? "" : String.valueOf(v)));
        p.put("headers", h);
        if (body != null) p.put("body", jsonSafe(body));
        return p;
    }

    private static Object jsonSafe(Object body) {
        if (body instanceof String text) return text.length() > MAX_BODY ? text.substring(0, MAX_BODY) + "…" : text;
        try {
            String json = Json.write(body);
            return json.length() > MAX_BODY ? json.substring(0, MAX_BODY) + "…" : Json.parse(json);
        } catch (RuntimeException notJson) {
            return String.valueOf(body);
        }
    }

    private static String level(Level level) {
        int v = level.intValue();
        if (v >= Level.SEVERE.intValue()) return "error";
        if (v >= Level.WARNING.intValue()) return "warn";
        if (v >= Level.INFO.intValue()) return "info";
        if (v >= Level.FINE.intValue()) return "debug";
        return "trace";
    }
}
