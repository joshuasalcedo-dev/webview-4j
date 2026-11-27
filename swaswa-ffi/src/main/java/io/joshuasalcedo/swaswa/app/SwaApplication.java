package io.joshuasalcedo.swaswa.app;

import io.joshuasalcedo.swaswa.css.StyleSheet;
import io.joshuasalcedo.swaswa.ffi.App;
import io.joshuasalcedo.swaswa.ffi.AppMode;
import io.joshuasalcedo.swaswa.ffi.Closed;
import io.joshuasalcedo.swaswa.ffi.Theme;
import io.joshuasalcedo.swaswa.ffi.View;
import io.joshuasalcedo.swaswa.ffi.WindowOptions;
import io.joshuasalcedo.swaswa.html.Aside;
import io.joshuasalcedo.swaswa.html.Document;
import io.joshuasalcedo.swaswa.html.Footer;
import io.joshuasalcedo.swaswa.html.Header;
import io.joshuasalcedo.swaswa.html.HtmlElement;
import io.joshuasalcedo.swaswa.html.Main;
import io.joshuasalcedo.swaswa.html.Nav;
import io.joshuasalcedo.swaswa.html.Node;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A swaswa app, and the root component of its own page: what its document is. It is a {@link io.joshuasalcedo.swaswa.component.Container}
 * (it plays the page's {@code <body>}), holding the page's parts in HTML's order: {@link #getHeader()},
 * {@link #getNav()}, {@link #getMain()}, {@link #getAside()}, {@link #getFooter()}. Its document's own properties are
 * its: {@link #setTitle title} ({@code <title>}), {@link #setLang lang} ({@code <html lang>}), {@link #setTheme theme}
 * ({@code color-scheme}), {@link #addStyleSheet style sheets}, {@link #setDescription description} and
 * {@link #putMeta meta}. So the same app is a desktop window ({@link #launch}) or a web page ({@link #toHtml()},
 * {@link #export}), with nothing to translate.
 * <pre>{@code
 * public class Notes extends SwaApplication {
 *     @Override
 *     protected void build() {
 *         setTitle("Notes");
 *         getHeader().add(Html.h1("Notes"));
 *         getMain().add(Html.textarea().setPlaceholder("Write..."));
 *     }
 *
 *     public static void main(String[] args) {
 *         launch(args);
 *     }
 * }
 * }</pre>
 * Its life: {@link #init()} (settings, connections), {@link #build()} (the page), the window shows it
 * ({@link #started}), the person uses it; when its window closes it quits ({@link #quitsWithLastWindow()}), then
 * {@link #stop()}. Where its data goes follows {@link #mode()}: in development its build folder, in production the
 * system's place.
 */
public abstract class SwaApplication extends HtmlElement<SwaApplication> {

    private final Main main = new Main();
    private Header header;
    private Nav nav;
    private Aside aside;
    private Footer footer;
    private String title = getClass().getSimpleName();
    private String lang = "en";
    private String description;
    private final Map<String, String> meta = new LinkedHashMap<>();
    private Theme theme = Theme.SYSTEM;
    private final List<StyleSheet> styleSheets = new CopyOnWriteArrayList<>();
    private List<String> args = List.of();
    private volatile App app;
    private volatile View window;
    private volatile Document document;

    protected SwaApplication() {
        super("body", false);
        add(main);
    }

    // ---- its life ----

    /** Before anything shows: on the thread that launched it (read settings, connect). */
    protected void init() throws Exception {}

    /** Makes its page: what its header, main part and the rest hold. Before its window opens. */
    protected abstract void build() throws Exception;

    /** Its window is showing its page. Not on the event loop's thread: waiting ({@code join()}) is fine here. */
    protected void started(App app, View window) throws Exception {}

    /** It has quit and its window is gone: on the thread that launched it, before {@code launch} returns. */
    protected void stop() throws Exception {}

    /** Whether it quits when its last window closes (true unless overridden: false for an app that lives in the tray). */
    protected boolean quitsWithLastWindow() {
        return true;
    }

    /** Its window: size, place, frame. The page is its own; the title follows {@link #setTitle}. */
    protected WindowOptions window() {
        return WindowOptions.html("").size(1000, 700);
    }

    // ---- the page's parts, in HTML's order ----

    /** What it is about ({@code <main>}): always there. */
    public Main getMain() {
        return main;
    }

    /** Its banner ({@code <header>}): made the first time it is asked for, first in the page. */
    public synchronized Header getHeader() {
        if (header == null) header = landmark(new Header());
        return header;
    }

    /** Its navigation ({@code <nav>}): after the header. */
    public synchronized Nav getNav() {
        if (nav == null) nav = landmark(new Nav());
        return nav;
    }

    /** Beside the main part ({@code <aside>}): after it. */
    public synchronized Aside getAside() {
        if (aside == null) aside = landmark(new Aside());
        return aside;
    }

    /** Its foot ({@code <footer>}): last. */
    public synchronized Footer getFooter() {
        if (footer == null) footer = landmark(new Footer());
        return footer;
    }

    /** A part put where HTML's order has it: header, nav, main, aside, footer. */
    private <T extends HtmlElement<T>> T landmark(T part) {
        int rank = switch (part.getTag()) { case "header" -> 0; case "nav" -> 1; case "aside" -> 3; default -> 4; };
        Node[] ranked = {header, nav, main, aside, footer};
        int index = 0;
        for (int r = 0; r < rank; r++) {
            if (ranked[r] != null) index = Math.max(index, getComponents().indexOf(ranked[r]) + 1);
        }
        add(part, rank == 4 ? getComponentCount() : index);
        return part;
    }

    // ---- the document's own properties ----

    /** Its {@code <title>}: the window's title, the browser tab's (not a tool tip, as on other components). */
    @Override
    public synchronized SwaApplication setTitle(String title) {
        this.title = Objects.requireNonNull(title, "title");
        Document d = document;
        if (d != null) d.setTitle(title);
        View w = window;
        if (w != null) w.setTitle(title);
        return this;
    }

    @Override
    public synchronized String getTitle() {
        return title;
    }

    /** Its language ({@code <html lang>}): {@code "en"} unless set. */
    @Override
    public synchronized SwaApplication setLang(String lang) {
        this.lang = Objects.requireNonNull(lang, "lang");
        Document d = document;
        if (d != null) d.setLang(lang);
        return this;
    }

    @Override
    public synchronized String getLang() {
        return lang;
    }

    /** What it is, in a sentence ({@code <meta name="description">}). */
    public synchronized SwaApplication setDescription(String description) {
        this.description = description;
        return this;
    }

    public synchronized String getDescription() {
        return description;
    }

    /** A {@code <meta name content>} ({@code "author"}, {@code "theme-color"}...); null takes it off. */
    public synchronized SwaApplication putMeta(String name, String content) {
        if (content == null) meta.remove(name);
        else meta.put(Objects.requireNonNull(name, "name"), content);
        return this;
    }

    public synchronized Map<String, String> getMeta() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(meta));
    }

    /** Light, dark, or the system's ({@code color-scheme}, and its window's): {@code Style.lightDark} colours follow. */
    public synchronized SwaApplication setTheme(Theme theme) {
        this.theme = Objects.requireNonNull(theme, "theme");
        Document d = document;
        if (d != null) d.setTheme(theme);
        return this;
    }

    public synchronized Theme getTheme() {
        return theme;
    }

    /** Its CSS, in order ({@code <style>} in its head): what changes in a sheet shows at once. */
    public synchronized SwaApplication addStyleSheet(StyleSheet sheet) {
        styleSheets.add(Objects.requireNonNull(sheet, "sheet"));
        Document d = document;
        if (d != null) d.addStyleSheet(sheet);
        return this;
    }

    public synchronized SwaApplication removeStyleSheet(StyleSheet sheet) {
        styleSheets.remove(sheet);
        Document d = document;
        if (d != null) d.removeStyleSheet(sheet);
        return this;
    }

    /**
     * A CSS file of the app's, on the class path ({@code "app.css"}), as a page's {@code <link rel="stylesheet">}:
     * {@link StyleSheet#load} (its imports followed, Tailwind's {@code @apply} done if swaswa-tailwind is there).
     */
    public SwaApplication addStyleSheet(String resource) {
        return addStyleSheet(StyleSheet.load(resource, getClass().getClassLoader()));
    }

    public List<StyleSheet> getStyleSheets() {
        return List.copyOf(styleSheets);
    }

    // ---- as a web page ----

    /** Its whole document as HTML: {@code <!doctype html><html lang><head>...</head><body>...</body></html>}. */
    @Override
    public synchronized String toHtml() {
        made();   // a page not yet made (no window: for the web) is made first
        StringBuilder b = new StringBuilder("<!doctype html>\n<html lang=\"").append(escape(lang)).append("\" style=\"color-scheme: ")
                .append(scheme(theme)).append("\">\n<head>").append(head()).append("</head>\n");
        return b.append(super.toHtml()).append("\n</html>\n").toString();
    }

    /** Writes its document to {@code directory/index.html}, for the web; returns that file. */
    public Path export(Path directory) throws IOException {
        Files.createDirectories(directory);
        Path file = directory.resolve("index.html");
        Files.writeString(file, toHtml(), StandardCharsets.UTF_8);
        return file;
    }

    private String head() {
        StringBuilder h = new StringBuilder("<meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");
        h.append("<title>").append(escape(title)).append("</title>");
        if (description != null) h.append("<meta name=\"description\" content=\"").append(escape(description)).append("\">");
        meta.forEach((n, c) -> h.append("<meta name=\"").append(escape(n)).append("\" content=\"").append(escape(c)).append("\">"));
        // as its window has them: each sheet after what it requires, then the typed classes' bases and rules, in their order
        java.util.Set<StyleSheet> written = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (StyleSheet sheet : styleSheets) style(sheet, written, h);
        java.util.Map<String, io.joshuasalcedo.swaswa.css.CssClass> used = new LinkedHashMap<>();
        typed(this, used);
        List<io.joshuasalcedo.swaswa.css.CssClass> ordered = new ArrayList<>(used.values());
        ordered.sort(java.util.Comparator.comparingLong(io.joshuasalcedo.swaswa.css.CssClass::order));   // stable: then first use
        StringBuilder rules = new StringBuilder();
        for (io.joshuasalcedo.swaswa.css.CssClass c : ordered) {
            String css = c.css();
            if (css == null) continue;
            if (c.base() != null) style(c.base(), written, h);
            rules.append(css).append('\n');
        }
        if (!rules.isEmpty()) h.append("<style>").append(rules).append("</style>");
        return h.toString();
    }

    private boolean made;

    /** Its page, made once: init, its own style sheet, build. */
    private synchronized void made() {
        if (made) return;
        made = true;
        try {
            init();
            // its own style sheet, as a page has its: a CSS file named as its class, beside it (MyApp.css)
            String own = getClass().getName().replace('.', '/') + ".css";
            if (getClass().getClassLoader().getResource(own) != null) addStyleSheet(own);
            build();
        } catch (Exception e) {
            throw new IllegalStateException(getClass().getSimpleName() + " did not start: " + e.getMessage(), e);
        }
    }

    private static void style(StyleSheet sheet, java.util.Set<StyleSheet> written, StringBuilder h) {
        if (!written.add(sheet)) return;
        for (StyleSheet first : sheet.requires()) style(first, written, h);
        h.append("<style>").append(sheet.toCss()).append("</style>");
    }

    private static void typed(Node node, java.util.Map<String, io.joshuasalcedo.swaswa.css.CssClass> used) {
        if (!(node instanceof HtmlElement<?> element)) return;
        for (io.joshuasalcedo.swaswa.css.CssClass c : element.getTypedCssClasses()) used.putIfAbsent(c.className(), c);
        for (Node child : element.getComponents()) typed(child, used);
    }

    private static String scheme(Theme theme) {
        return switch (theme) {
            case LIGHT -> "light";
            case DARK -> "dark";
            case SYSTEM -> "light dark";
        };
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    // ---- running ----

    /** What it was launched with. */
    public final List<String> args() {
        return args;
    }

    /** Development or production: where its data goes ({@link AppMode}). */
    public final AppMode mode() {
        return AppMode.current();
    }

    /** The running app, once its window opens; null before. */
    public final App app() {
        return app;
    }

    /** Its window, once open; null before. */
    public final View getWindow() {
        return window;
    }

    /** Its page, live, once its window shows it; null before. */
    public final Document getDocument() {
        return document;
    }

    /** Quits: its window closes, then {@link #stop()} runs. */
    public final void quit() {
        App a = app;
        if (a != null) a.quit();
    }

    /**
     * Launches the app whose {@code main} calls this: makes it (its constructor with no arguments), and runs it on this
     * thread until it quits (the main thread, as macOS needs). Returns the event loop's exit code.
     *
     * @throws IllegalStateException if not called from a class that extends SwaApplication
     */
    public static int launch(String... args) {
        Class<?> caller = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                .walk(frames -> frames.map(StackWalker.StackFrame::getDeclaringClass)
                        .filter(c -> c != SwaApplication.class && SwaApplication.class.isAssignableFrom(c))
                        .findFirst().orElse(null));
        if (caller == null) {
            throw new IllegalStateException("launch(args) is called from the app's own class (one that extends SwaApplication):"
                    + " or call launch(MyApp.class, args)");
        }
        return launch(caller.asSubclass(SwaApplication.class), args);
    }

    /** Launches {@code type}: makes it (its constructor with no arguments) and runs it until it quits. */
    public static int launch(Class<? extends SwaApplication> type, String... args) {
        Objects.requireNonNull(type, "type");
        SwaApplication application;
        try {
            Constructor<? extends SwaApplication> made = type.getDeclaredConstructor();
            made.setAccessible(true);
            application = made.newInstance();
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new IllegalStateException(type.getName() + " could not be made: it needs a constructor with no arguments", e);
        }
        return application.run(args);
    }

    /** Runs it until it quits: init, build, its window, the person's use, stop. */
    public final int run(String... launchedWith) {
        this.args = List.of(launchedWith == null ? new String[0] : launchedWith);
        AtomicReference<Throwable> failed = new AtomicReference<>();
        made();
        int code = App.run(running -> {
            app = running;
            if (quitsWithLastWindow()) {
                running.subscribe(Closed.class, closed -> {
                    if (running.views().join().isEmpty()) running.quit();
                });
            }
            try {
                View opened = running.open(window().title(getTitle())).join();
                window = opened;
                Document d = Document.of(opened, this);
                synchronized (this) {
                    d.setTitle(title).setLang(lang);
                    for (StyleSheet sheet : styleSheets) d.addStyleSheet(sheet);
                    document = d;
                }
                d.setTheme(theme).join();
                started(running, opened);
            } catch (Throwable e) {
                failed.set(e);
                running.quit();
            }
        });
        try {
            stop();
        } catch (Exception e) {
            if (failed.get() == null) failed.set(e);
        }
        Throwable f = failed.get();
        if (f != null) throw new IllegalStateException(getClass().getSimpleName() + " failed", f);
        return code;
    }
}
