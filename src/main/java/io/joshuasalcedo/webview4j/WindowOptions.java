package io.joshuasalcedo.webview4j;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * How {@link App#open} makes a window: what it shows first and how it looks.
 * <pre>{@code
 * app.open(WindowOptions.url("https://example.com").title("Example").size(1280, 800))
 * app.open(WindowOptions.html("<h1>Hi</h1>").capture(Capture.CLICKS, Capture.CONSOLE))
 * }</pre>
 */
public final class WindowOptions {

    private String url;
    private String html;
    private String title = "WebView";
    private double width = 1024;
    private double height = 768;
    private boolean resizable = true;
    private boolean decorations = true;
    private boolean devtools;
    private boolean headless;
    private String initializationScript;
    private Path dataDirectory;
    private Set<Capture> capture;

    private WindowOptions() {}

    public static WindowOptions url(String url) {
        WindowOptions options = new WindowOptions();
        options.url = Objects.requireNonNull(url, "url");
        return options;
    }

    public static WindowOptions html(String html) {
        WindowOptions options = new WindowOptions();
        options.html = Objects.requireNonNull(html, "html");
        return options;
    }

    public static WindowOptions blank() {
        return new WindowOptions();
    }

    public WindowOptions title(String title)        { this.title = Objects.requireNonNull(title, "title"); return this; }
    public WindowOptions resizable(boolean value)   { this.resizable = value; return this; }
    /** False removes the title bar and borders. */
    public WindowOptions decorations(boolean value) { this.decorations = value; return this; }
    public WindowOptions devtools(boolean value)    { this.devtools = value; return this; }
    /** True keeps the window hidden: for automation and tests. */
    public WindowOptions headless(boolean value)    { this.headless = value; return this; }

    /** The inner size, in logical pixels. */
    public WindowOptions size(double width, double height) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("size must be positive");
        this.width = width;
        this.height = height;
        return this;
    }

    /** A script that runs in every page before the page's own, on every navigation. */
    public WindowOptions initializationScript(String script) {
        this.initializationScript = script;
        return this;
    }

    /** A persistent browser profile (cookies, storage): one directory per account. Must be absolute. */
    public WindowOptions dataDirectory(Path directory) {
        if (directory != null && !directory.isAbsolute()) throw new IllegalArgumentException("dataDirectory must be absolute");
        this.dataDirectory = directory;
        return this;
    }

    /** Exactly these reports on from the start; the rest off. */
    public WindowOptions capture(Capture... on) {
        this.capture = on.length == 0 ? EnumSet.noneOf(Capture.class) : EnumSet.of(on[0], on);
        return this;
    }

    /** The JSON payload of {@link WindowAction#OPEN}. */
    Map<String, Object> toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (url != null) payload.put("url", url);
        if (html != null) payload.put("html", html);
        payload.put("title", title);
        payload.put("width", width);
        payload.put("height", height);
        payload.put("resizable", resizable);
        payload.put("decorations", decorations);
        payload.put("devtools", devtools);
        payload.put("headless", headless);
        if (initializationScript != null) payload.put("initializationScript", initializationScript);
        if (dataDirectory != null) payload.put("dataDirectory", dataDirectory.toString());
        if (capture != null) payload.put("capture", switches(capture));
        return payload;
    }

    /** Every report on or off, as the capture payload. */
    static Map<String, Object> switches(Set<Capture> on) {
        Map<String, Object> switches = new LinkedHashMap<>();
        for (Capture kind : Capture.values()) switches.put(kind.key(), on.contains(kind));
        return switches;
    }
}
