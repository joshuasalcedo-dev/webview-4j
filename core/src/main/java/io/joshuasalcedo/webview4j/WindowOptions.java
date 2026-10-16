package io.joshuasalcedo.webview4j;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
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
    private List<String> origins;
    private String assets;
    private ClassLoader assetLoader;
    private byte[] icon;

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

    /**
     * The app's own page: the window opens {@code <directory>/index.html} from the class path,
     * served with everything it links to (scripts, styles, fonts, images) over webview-4j's custom
     * protocol at {@code <base>app/}. No port is opened and nothing is fetched from the network. The
     * page may use the bridge and the controllers; by default no other origin may.
     *
     * <p>Files are read with the thread's context class loader; {@link #app(String, ClassLoader)}
     * names another. Only files under {@code directory} are served, by paths of letters, digits and
     * {@code ._-~@+} (no {@code ..}, no hidden files, nothing percent-encoded).
     *
     * @param directory a class path directory, such as {@code "app"} for {@code src/main/resources/app}
     */
    public static WindowOptions app(String directory) {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        return app(directory, loader != null ? loader : WindowOptions.class.getClassLoader());
    }

    /** {@link #app(String)}, reading the files with {@code loader}. */
    public static WindowOptions app(String directory, ClassLoader loader) {
        WindowOptions options = new WindowOptions();
        options.assets = Assets.root(directory);
        options.assetLoader = Objects.requireNonNull(loader, "loader");
        options.url = Assets.baseUrl() + "app/index.html";
        return options;
    }

    public WindowOptions title(String title)        { this.title = Objects.requireNonNull(title, "title"); return this; }

    /**
     * The window's icon, a PNG (a square of 256 pixels reads well everywhere): in its title bar,
     * the taskbar and the window switcher, instead of the Java program's. A picture that is not a
     * PNG leaves the program's icon, with a warning in the log.
     *
     * @throws IllegalArgumentException if it is empty or over a megabyte
     */
    public WindowOptions icon(byte[] png) {
        Objects.requireNonNull(png, "png");
        if (png.length == 0 || png.length > 1024 * 1024) {
            throw new IllegalArgumentException("an icon is a PNG of 1 byte to 1 MB, not " + png.length + " bytes");
        }
        this.icon = png.clone();
        return this;
    }

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

    /**
     * The only origins whose pages may call {@link App#handle commands} and receive
     * {@link View#emit emits}, e.g. {@code "https://app.example"} or {@code "http://localhost:5173"}.
     * Unless given: the origin of the {@link #url url}, or {@code "null"} for {@link #html html}.
     * Any other page the window navigates to gets {@code 403}.
     */
    public WindowOptions origins(String... origins) {
        for (String origin : origins) {
            if (!origin.equals("null") && !origin.matches("https?://[^/?#\\s]+")) {
                throw new IllegalArgumentException("not an origin (scheme://host[:port], or null): " + origin);
            }
        }
        this.origins = List.of(origins);
        return this;
    }

    /** The JSON payload of {@code app.open} (PROTOCOL.md §5.1). */
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
        if (origins != null) payload.put("origins", origins);
        if (assets != null) payload.put("assets", assets);
        if (icon != null) payload.put("icon", java.util.Base64.getEncoder().encodeToString(icon));
        return payload;
    }

    /** The app's files, as a class path directory; null when the window serves none. */
    String assets() {
        return assets;
    }

    ClassLoader assetLoader() {
        return assetLoader;
    }

    /** Every report on or off, as the capture payload. */
    static Map<String, Object> switches(Set<Capture> on) {
        Map<String, Object> switches = new LinkedHashMap<>();
        for (Capture kind : Capture.values()) switches.put(kind.key(), on.contains(kind));
        return switches;
    }
}
