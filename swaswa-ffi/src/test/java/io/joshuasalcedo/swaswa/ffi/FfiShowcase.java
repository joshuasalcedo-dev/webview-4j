package io.joshuasalcedo.swaswa.ffi;

import static io.joshuasalcedo.swaswa.ffi.ShowcaseUi.button;
import static io.joshuasalcedo.swaswa.ffi.ShowcaseUi.hint;
import static io.joshuasalcedo.swaswa.ffi.ShowcaseUi.row;
import static io.joshuasalcedo.swaswa.ffi.ShowcaseUi.section;

import io.joshuasalcedo.swaswa.app.SwaApplication;
import io.joshuasalcedo.swaswa.css.CssProperty;
import io.joshuasalcedo.swaswa.css.Style;
import io.joshuasalcedo.swaswa.css.StyleSheet;
import io.joshuasalcedo.swaswa.html.A;
import io.joshuasalcedo.swaswa.html.Button;
import io.joshuasalcedo.swaswa.html.Dd;
import io.joshuasalcedo.swaswa.html.Div;
import io.joshuasalcedo.swaswa.html.Document;
import io.joshuasalcedo.swaswa.html.Dl;
import io.joshuasalcedo.swaswa.html.Html;
import io.joshuasalcedo.swaswa.html.Input;
import io.joshuasalcedo.swaswa.html.Select;
import io.joshuasalcedo.swaswa.html.Table;
import io.joshuasalcedo.swaswa.html.Tr;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Everything swaswa-ffi does, built on what it gives: the control panel is a {@link SwaApplication} (its page made of
 * components, each button a Java listener), and every window it opens is made of components too. A target window
 * beside it is what the panel changes with {@link App} and {@link View}; every event the native side reports is listed
 * in the panel as it comes. Not part of the jar (a test source). {@code just ffi-showcase}.
 */
public final class FfiShowcase extends SwaApplication {

    public static void main(String[] args) {
        launch(args);
    }

    /** The control panel's window, and the one it changes. */
    static volatile View panel;
    private static volatile View target;
    private static volatile Target targetPage;
    private static final Map<String, Boolean> toggles = new java.util.concurrent.ConcurrentHashMap<>();
    /** The network calls under way: "view/id" -> "METHOD url". */
    private static final Map<String, String> calls = new java.util.concurrent.ConcurrentHashMap<>();

    /** What the last button did, and the events as they come. */
    private final Div said = Html.div(Html.text("Press a button.")).cssClass("mono").setId("said");
    private final Div log = Html.div().cssClass("mono").setId("log");
    private final Table windows = Html.table();

    @Override
    protected boolean quitsWithLastWindow() {
        return false;   // the panel's own window ends it (below), not the last of many
    }

    @Override
    protected WindowOptions window() {
        return WindowOptions.html("").size(760, 940).position(40, 40).minSize(600, 500).devtools(true);
    }

    @Override
    protected void build() {
        setTitle("swaswa-ffi: control panel");
        addStyleSheet(ShowcaseUi.LOOK);
        addStyleSheet(new StyleSheet()
                .rule("body", s -> s.set(CssProperty.DISPLAY, "grid").set(CssProperty.GRID_TEMPLATE_ROWS, "auto 1fr 300px")
                        .set(CssProperty.HEIGHT, "100vh"))
                .rule("header", s -> s.set(CssProperty.PADDING, "16px 18px 0"))
                .rule("header h1", s -> s.set(CssProperty.FONT_SIZE, "20px").set(CssProperty.MARGIN, "0 0 4px"))
                .rule("main", s -> s.set(CssProperty.OVERFLOW, "auto").set(CssProperty.PADDING, "8px 18px 16px"))
                .rule("aside", s -> s.set(CssProperty.DISPLAY, "flex").set(CssProperty.FLEX_DIRECTION, "column").set(CssProperty.MIN_HEIGHT, "0")
                        .set(CssProperty.BORDER_TOP, "1px solid " + Style.var("line")).set(CssProperty.BACKGROUND, Style.var("card")))
                .rule("#said", s -> s.set(CssProperty.PADDING, "10px 14px").set(CssProperty.BORDER_BOTTOM, "1px solid " + Style.var("line"))
                        .set(CssProperty.MIN_HEIGHT, "48px").set(CssProperty.WHITE_SPACE, "pre-wrap").set(CssProperty.FONT_SIZE, "12px"))
                .rule("#said img", s -> s.set(CssProperty.DISPLAY, "block").set(CssProperty.MAX_WIDTH, "100%").set(CssProperty.MARGIN_TOP, "6px"))
                .rule("#log", s -> s.set(CssProperty.FLEX, "1").set(CssProperty.OVERFLOW, "auto").set(CssProperty.FONT_SIZE, "11.5px")
                        .set(CssProperty.PADDING, "6px 10px"))
                .rule("#log div", s -> s.set(CssProperty.PADDING, "2px 0").set(CssProperty.BORDER_BOTTOM, "1px dashed " + Style.var("line")))
                .rule("#log b", s -> s.set(CssProperty.COLOR, Style.var("accent")))
                .rule("table", s -> s.set(CssProperty.WIDTH, "100%").set(CssProperty.BORDER_COLLAPSE, "collapse").set(CssProperty.MARGIN_TOP, "8px")
                        .set(CssProperty.FONT_SIZE, "12px"))
                .rule("td, th", s -> s.set(CssProperty.TEXT_ALIGN, "left").set(CssProperty.PADDING, "4px 6px").set(CssProperty.WHITE_SPACE, "nowrap")
                        .set(CssProperty.BORDER_BOTTOM, "1px solid " + Style.var("line")))
                .rule("td.t", s -> s.set(CssProperty.MAX_WIDTH, "180px").set(CssProperty.OVERFLOW, "hidden").set(CssProperty.TEXT_OVERFLOW, "ellipsis"))
                .rule("td button", s -> s.set(CssProperty.PADDING, "2px 6px").set(CssProperty.FONT_SIZE, "11px")));

        getHeader().add(Html.h1("swaswa-ffi"), Html.p(Html.text("Every button calls Java, which changes the "), Html.b("target window"),
                Html.text(" with App and View (closed, it opens again). "), act("Bring the target window forward", () -> {
                    target().focus().join();
                    return "the target window, in front";
                })).style(CssProperty.MARGIN, "0 0 8px"));
        getAside().add(said, log);

        Input title = Html.input().setValue("swaswa-ffi").setPlaceholder("A title");
        Select theme = select("SYSTEM", "LIGHT", "DARK");
        Select progress = select("NORMAL", "INDETERMINATE", "PAUSED", "ERROR", "NONE");
        Select material = select("MICA", "ACRYLIC", "TABBED");
        Input sentence = Html.input().setValue("This are a sentence with a speling mistake.").setSize(40);

        getMain().add(
                section("The target window",
                        row(act("Minimize", () -> done(target().minimize(), "minimized")),
                                act("Maximize", () -> done(target().maximize(), "maximized")),
                                act("Restore", () -> done(target().restore(), "restored")),
                                act("Fullscreen on/off", () -> { boolean on = flip("fullscreen"); return done(target().setFullscreen(on), "fullscreen " + on); }),
                                act("Center", () -> done(target().center(), "centered on its monitor")),
                                act("Always on top on/off", () -> { boolean on = flip("onTop"); return done(target().setAlwaysOnTop(on), "always on top " + on); }),
                                act("Resizable on/off", () -> { boolean off = flip("notResizable"); return done(target().setResizable(!off), "resizable " + !off); }),
                                act("Min 600×400", () -> done(target().setMinSize(600, 400), "min size 600 x 400")),
                                act("Max 1400×1000", () -> done(target().setMaxSize(1400, 1000), "max size 1400 x 1000")),
                                act("No max", () -> done(target().clearMaxSize(), "no max size"))),
                        row(act("Minimize button on/off", () -> { boolean off = flip("notMinimizable"); return done(target().setMinimizable(!off), "minimize button " + !off); }),
                                act("Maximize button on/off", () -> { boolean off = flip("notMaximizable"); return done(target().setMaximizable(!off), "maximize button " + !off); }),
                                act("Close button on/off", () -> { boolean off = flip("notClosable"); return done(target().setClosable(!off), "close button " + !off); }),
                                act("Taskbar entry on/off", () -> { boolean on = flip("skipTaskbar"); return done(target().setSkipTaskbar(on), "no taskbar entry " + on); }),
                                act("Hide from capture on/off", () -> { boolean on = flip("protect"); return done(target().setContentProtection(on), "hidden from screen capture " + on); }),
                                act("Frame and title bar on/off", () -> {
                                    boolean off = flip("noFrame");
                                    return done(target().setDecorations(!off), "frame and title bar " + !off + (off ? " (resize it from its edges)" : ""));
                                })),
                        row(title, act("Set title", () -> {
                                    String t = title.getValue() == null || title.getValue().isBlank() ? "swaswa-ffi" : title.getValue();
                                    return done(target().setTitle(t), "title: " + t);
                                }),
                                theme, act("Theme", () -> done(target().setTheme(Theme.valueOf(theme.getValue())), "theme " + theme.getValue())),
                                act("Geometry", () -> target().geometry().join().toString()),
                                act("Monitors", () -> target().monitors().join().toString()))),
                section("The target's taskbar button",
                        row(progress, act("Progress", () -> {
                                    String p = progress.getValue();
                                    boolean amount = p.equals("NORMAL") || p.equals("PAUSED") || p.equals("ERROR");
                                    return done(target().setProgress(View.Progress.valueOf(p), amount ? 40 : -1), "taskbar progress " + p);
                                }),
                                act("Badge 3", () -> done(target().setBadge(3), "badge 3 (the macOS dock; Windows to come)")),
                                act("No badge", () -> done(target().setBadge(0), "no badge")),
                                act("Ask for attention", () -> {
                                    View t = target();
                                    // after a moment: the window must not have the focus for the taskbar to flash
                                    CompletableFuture.delayedExecutor(3, java.util.concurrent.TimeUnit.SECONDS).execute(() -> t.requestAttention(View.Attention.CRITICAL));
                                    return "in 3 seconds: switch to another window to see the taskbar flash";
                                }))),
                section("Other windows",
                        row(act("Frameless, own title bar", () -> ShowcaseWindows.frameless(app())),
                                act("Owned (child) window", () -> ShowcaseWindows.owned(app(), target())),
                                act("Embedded view", () -> ShowcaseWindows.embeddedIn(target())),
                                act("Transparent, round", () -> ShowcaseWindows.transparent(app())),
                                act("Palette (no focus, no capture)", () -> ShowcaseWindows.palette(app(), target()))),
                        row(material, act("Windows 11 backdrop", () -> ShowcaseWindows.backdrop(app(), material.getValue())),
                                hint("Several webviews in one window: the embedded view; parent and child: the owned window."))),
                section("Webviews",
                        row(hint("An external site in a window of its own:"),
                                act("example.com", () -> site("https://example.com/")),
                                act("Wikipedia", () -> site("https://en.wikipedia.org/wiki/WebView")),
                                act("swaswa on GitHub", () -> site("https://github.com/joshuasalcedo-dev/swaswa"))),
                        row(hint("Our own UI around an external site (an embedded webview):"),
                                act("Browser: Wikipedia", () -> ShowcaseWindows.browser(app(), "https://en.wikipedia.org/wiki/WebView")),
                                act("Browser: YouTube", () -> ShowcaseWindows.browser(app(), "https://www.youtube.com/"))),
                        row(hint("Graphics: pixels made in Java, drawn on a page's canvas:"),
                                act("Java draws a canvas", () -> ShowcaseWindows.canvas(app())),
                                act("Sketch pad: you draw, Java draws it", () -> ShowcaseWindows.sketch(app())),
                                act("egui window, drawn from Java", () -> ShowcaseWindows.egui(app())),
                                act("egui: a fast log that follows its end", () -> ShowcaseWindows.eguiLog(app())),
                                act("egui: title bars of its own", () -> ShowcaseWindows.eguiFrameless(app())),
                                act("egui inside a page", () -> ShowcaseWindows.eguiInPage(app()))),
                        row(hint("One window, several webviews (YouTube, Wikipedia, example.com, GitHub, our own page):"),
                                act("Several webviews, one window", () -> ShowcaseWindows.multi(app())))),
                section("Injecting into a site (Wikipedia)",
                        row(act("Open Wikipedia (with an initialization script)", () -> "opened " + ShowcaseWindows.injected(app(), true).id()),
                                hint("a badge on every page it loads, put in before the page's own scripts")),
                        row(hint("Inject CSS:"),
                                act("Sepia, serif", () -> ShowcaseWindows.injectCss(app(), "SEPIA")),
                                act("Hide the sidebars", () -> ShowcaseWindows.injectCss(app(), "FOCUS")),
                                act("Highlight links", () -> ShowcaseWindows.injectCss(app(), "LINKS")),
                                act("Take it out", () -> ShowcaseWindows.injectCss(app(), "NONE"))),
                        row(act("Inject a script", () -> ShowcaseWindows.injectScript(app())),
                                hint("it adds a banner, and what it returns (the title, its links) comes back to Java")),
                        row(hint("Listen to the page (each on/off; what it does comes to the log):"),
                                act("Network calls", () -> ShowcaseWindows.listen(app(), Capture.NETWORK, flip("listen-NETWORK"), this::told)),
                                act("WebSockets", () -> ShowcaseWindows.listen(app(), Capture.WEBSOCKET, flip("listen-WEBSOCKET"), this::told)),
                                act("Console", () -> ShowcaseWindows.listen(app(), Capture.CONSOLE, flip("listen-CONSOLE"), this::told)),
                                act("Clicks", () -> ShowcaseWindows.listen(app(), Capture.CLICKS, flip("listen-CLICKS"), this::told)))),
                section("HTML and CSS from Java",
                        row(act("A page of HTML's elements, made in Java", () -> {
                                    Document page = ShowcaseUi.window(app(), WindowOptions.html("").title("HTML and CSS from Java").size(560, 520));
                                    page.addStyleSheet(HtmlDemo.sheet());
                                    page.body().add(HtmlDemo.page(page));
                                    return "a page made in Java of HTML's elements and CSS (" + page.view().id() + ")";
                                }),
                                hint("typed elements, live: Java changes them, their events come to Java; CSS with light and dark"))),
                section("Docking and MDI: several views in one window",
                        row(act("Open the workspace", () -> ShowcaseWorkspace.open(app())),
                                hint("then open windows from its bar and drag them onto it (Ctrl: keep floating), or add MDI children inside it;"
                                        + " pop anything out again from its bar"))),
                section("Running windows",
                        row(hint("Arrange them:"),
                                act("Side by side", () -> arrange("TILE")), act("Grid", () -> arrange("GRID")), act("Cascade", () -> arrange("CASCADE"))),
                        windows),
                section("The target's page",
                        row(act("Zoom 80%", () -> done(target().setZoom(0.8), "zoom 80%")),
                                act("100%", () -> done(target().setZoom(1), "zoom 100%")),
                                act("125%", () -> done(target().setZoom(1.25), "zoom 125%")),
                                act("Reload", () -> { target().reload(); return "reloading"; }),
                                act("Print", () -> done(target().print(), "the print dialog")),
                                act("Developer tools", () -> done(target().openDevTools(), "developer tools open")),
                                act("User agent", () -> String.valueOf(target().eval("navigator.userAgent").join())),
                                act("Screenshot", this::screenshot),
                                act("Set a cookie", () -> {
                                    target().setCookie(new Cookie("showcase", "hello", "", "/", false, false)).join();
                                    return target().allCookies().join().toString();
                                }),
                                act("localStorage", () -> {
                                    target().localStorage().set("showcase", "kept in localStorage").join();
                                    return "localStorage: " + target().localStorage().get("showcase").join();
                                }),
                                act("Clear browsing data", () -> done(target().clearBrowsingData(), "cookies, storage and cache cleared"))),
                        row(hint("In the target: a new-window link (NewWindowRequested), a download (Download), shortcuts Ctrl+S, F5, Ctrl+Shift+P"
                                        + " (Shortcut), dropping a file (FileDrop)."),
                                act("Shortcuts off", () -> done(target().setShortcuts(), "the window's shortcuts off")),
                                act("On", () -> done(target().setShortcuts("Mod+S", "F5", "Mod+Shift+P"), "Mod+S, F5, Mod+Shift+P")))),
                section("The system",
                        row(act("Open file", () -> String.valueOf(app().openFile(new FileDialog().title("Open a file").parent(target())).join().orElse(null))),
                                act("Save file", () -> String.valueOf(app().saveFile(new FileDialog().title("Save as").fileName("notes.txt").parent(target())).join().orElse(null))),
                                act("Pick folder", () -> String.valueOf(app().pickFolder(new FileDialog().title("Pick a folder").parent(target())).join().orElse(null))),
                                act("Message box", () -> String.valueOf(app().message(MessageDialog.of("swaswa-ffi", "A native message box.").parent(target())).join())),
                                act("Confirm", () -> "confirmed: " + app().confirm("swaswa-ffi", "Is this a native dialog?").join()),
                                act("Secret store", () -> {
                                    SecretStore store = SecretStore.of("swaswa-showcase");
                                    store.put("demo", "s3cret at " + java.time.LocalTime.now().withNano(0));
                                    String back = store.get("demo").orElse("(none)");
                                    store.delete("demo");
                                    return "the system's vault kept and gave back: " + back;
                                })),
                        row(sentence, act("Check spelling and grammar", () -> {
                            String text = sentence.getValue() == null ? "" : sentence.getValue();
                            var mistakes = Grammar.english().check(text);
                            return mistakes.isEmpty() ? "no mistakes" : mistakes.stream().map(m -> m.found(text) + ": " + m.message()
                                    + (m.suggestions().isEmpty() ? "" : " → " + m.suggestions().getFirst())).toList().toString();
                        }))));
    }

    @Override
    protected void started(App app, View window) {
        panel = window;
        app.subscribe(WebViewEvent.class, this::show);
        ShowcaseWindows.follow(app);
        target();
        String id = window.id();
        app.subscribe(Closed.class, closed -> {
            if (closed.view().equals(id)) app.quit();
        });
        // every window of the app, as it is now
        Thread.ofVirtual().name("showcase-windows").start(() -> {
            while (app.whenStopped().isDone() == false) {
                try {
                    refreshWindows(app);
                    Thread.sleep(1500);
                } catch (Exception stopped) {
                    return;
                }
            }
        });
    }

    // ---- what the panel shows ----

    /** A button whose press does {@code action}, and says what it did. */
    private Button act(String label, Action action) {
        return button(label, () -> {
            said.setText("…");
            try {
                say(action.run());
            } catch (Exception e) {
                Throwable cause = e instanceof java.util.concurrent.CompletionException && e.getCause() != null ? e.getCause() : e;
                said.setText("failed: " + cause.getMessage());
            }
        });
    }

    @FunctionalInterface
    interface Action {
        Object run() throws Exception;
    }

    /** A picture to show under what was said. */
    record Picture(String text, byte[] png) {}

    private void say(Object what) {
        if (what instanceof Picture p) {
            said.removeAll();
            said.add(Html.text(p.text()), Html.img().setSrc("data:image/png;base64," + Base64.getEncoder().encodeToString(p.png())).setAlt("the screenshot"));
        } else {
            said.setText(String.valueOf(what));
        }
    }

    /** A line in the log, newest first; the oldest go past 300. */
    void told(String kind, String text) {
        log.add(Html.div(Html.b(kind), Html.text(" " + text)), 0);
        while (log.getComponentCount() > 300) log.remove(log.getComponents().getLast());
    }

    /** One event: to the log (the noisy ones left out), and the target's own to the target's page. */
    private void show(WebViewEvent event) {
        View p = panel;
        if (p == null || event instanceof LogRecord l && l.source() == LogRecord.Source.NATIVE) return;
        if (event instanceof PageCall call && call.name().startsWith("swaswa.")) return;   // the pages' own events, to their listeners
        if (event.view() != null && event.view().equals(p.id()) && (event instanceof Moved || event instanceof Resized)) return;
        told(event.getClass().getSimpleName(), brief(event));
        View t = target;
        Target page = targetPage;
        if (t != null && page != null && t.id().equals(event.view())) page.told(event);
    }

    /** An event in one line: a network call as method, status, address and time; the rest as they are. */
    private static String brief(WebViewEvent event) {
        if (event instanceof NetworkEvent n) {
            String call = n.phase() == NetworkEvent.Phase.START ? null
                    : java.util.Objects.requireNonNullElse(calls.remove(n.view() + "/" + n.id()), n.method() + " " + n.url());
            return switch (n.phase()) {
                case START -> {
                    calls.put(n.view() + "/" + n.id(), n.method() + " " + n.url());
                    yield n.view() + "  → " + n.kind() + " " + n.method() + " " + n.url();
                }
                case END -> n.view() + "  ← " + n.status() + " " + call + "  " + n.durationMs() + " ms, " + n.size() + " bytes"
                        + (n.mimeType() == null ? "" : ", " + n.mimeType());
                case ERROR -> n.view() + "  ✕ " + call + ": " + n.error();
                case RESOURCE -> n.view() + "  · " + n.kind() + " " + n.url() + "  " + n.durationMs() + " ms";
            };
        }
        String text = event.toString();
        return text.length() > 300 ? text.substring(0, 300) + "…" : text;
    }

    /** Every window and view of the app: where it is and how it is shown, with buttons to act on each. */
    private void refreshWindows(App app) {
        Tr head = Html.tr(Html.th("View"), Html.th("Title"), Html.th("Where"), Html.th("Size"), Html.th("State"), Html.th(""));
        java.util.List<Tr> rows = new java.util.ArrayList<>();
        rows.add(head);
        for (ViewInfo info : app.views().join()) {
            View v = app.view(info.id());
            String where, size, state;
            try {
                Geometry g = v.geometry().join();
                where = (int) g.x() + ", " + (int) g.y();
                size = (int) g.width() + " × " + (int) g.height();
                state = (g.minimized() ? "minimized" : g.fullscreen() ? "fullscreen" : g.maximized() ? "maximized" : g.visible() ? "normal" : "hidden")
                        + (g.focused() ? " · focused" : "");
            } catch (RuntimeException embedded) {
                where = "embedded";
                size = "";
                state = "embedded";
            }
            Div acts = Html.div();
            if (!state.equals("embedded")) {
                acts.add(act("Focus", () -> done(v.focus(), "focused " + v.id())), act("Min", () -> done(v.minimize(), "minimized " + v.id())),
                        act("Restore", () -> done(v.restore(), "restored " + v.id())));
            }
            acts.add(act("Close", () -> done(v.close(), "closed " + v.id())));
            rows.add(Html.tr(Html.td(info.id() + (panel != null && info.id().equals(panel.id()) ? " (this panel)" : "")),
                    Html.td(String.valueOf(info.title())).cssClass("t").setToolTipText(String.valueOf(info.title())),
                    Html.td(where), Html.td(size), Html.td(state), Html.td(acts)));
        }
        windows.removeAll();
        for (Tr r : rows) windows.add(r);
    }

    // ---- what the buttons do ----

    private static boolean flip(String name) {
        boolean now = !toggles.getOrDefault(name, false);
        toggles.put(name, now);
        return now;
    }

    static String done(CompletableFuture<?> future, String said) {
        future.join();
        return said;
    }

    private String site(String url) {
        return "opened " + app().open(WindowOptions.url(url).title(url).size(1100, 780)).join().id();
    }

    private Picture screenshot() throws Exception {
        byte[] png = target().screenshot(0.5).join();
        Path file = Files.createTempFile("swaswa-screenshot-", ".png");
        Files.write(file, png);
        return new Picture("screenshot " + png.length + " bytes: " + file, png);
    }

    /** The app's windows, arranged on the main monitor: side by side, in a grid, or cascading. */
    private String arrange(String how) {
        java.util.List<View> shown = new java.util.ArrayList<>();
        for (ViewInfo info : app().views().join()) {
            View v = app().view(info.id());
            try {
                if (v.geometry().join().visible()) shown.add(v);
            } catch (RuntimeException embedded) {
                // embedded views move with their window
            }
        }
        Monitor monitor = panel.monitors().join().stream().filter(Monitor::primary).findFirst().orElseThrow();
        double scale = monitor.scaleFactor();
        double left = monitor.x(), top = monitor.y(), width = monitor.width(), height = monitor.height() - 48;   // the taskbar left free
        int n = shown.size();
        for (int i = 0; i < n; i++) {
            View v = shown.get(i);
            v.restore().join();
            double x, y, w, h;
            switch (how) {
                case "TILE" -> { w = width / n; h = height; x = left + i * w; y = top; }
                case "GRID" -> {
                    int cols = (int) Math.ceil(Math.sqrt(n)), rows = (int) Math.ceil(n / (double) cols);
                    w = width / cols; h = height / rows; x = left + (i % cols) * w; y = top + (i / cols) * h;
                }
                default -> { w = width * 0.55; h = height * 0.6; x = left + 40 + i * 36; y = top + 30 + i * 36; }
            }
            v.setBounds((int) Math.round(x * scale), (int) Math.round(y * scale), (int) Math.round(w * scale), (int) Math.round(h * scale)).join();
        }
        return "arranged " + n + " windows: " + how.toLowerCase();
    }

    private static Select select(String... options) {
        Select s = Html.select();
        for (String o : options) s.add(Html.option(o));
        s.setValue(options[0]);
        return s;
    }

    // ---- the target window ----

    /** The target window: open, or opened again (it may have been closed). */
    private synchronized View target() {
        View t = target;
        if (t != null && app().views().join().stream().anyMatch(v -> v.id().equals(t.id()))) return t;
        Document page = ShowcaseUi.window(app(), WindowOptions.html("").title("Target window").size(720, 560).position(820, 60).devtools(true)
                .fileDrop(true).newWindows(WindowOptions.NewWindows.JAVA).shortcuts("Mod+S", "F5", "Mod+Shift+P"));
        target = page.view();
        targetPage = new Target(page);
        toggles.clear();
        return target;
    }

    /** The target's page: what the window is now, as the panel changes it, kept by Java from the window's events. */
    private static final class Target {
        final Document page;
        final Dd size = Html.dd("—"), position = Html.dd("—"), shown = Html.dd("normal"), focused = Html.dd("—"), zoom = Html.dd("—"),
                scheme = Html.dd("—"), shortcut = Html.dd("— (press Ctrl+S, F5 or Ctrl+Shift+P here)");
        final Div last = Html.div("—").cssClass("mono"), drop = Html.div("Drop a file on this window");

        Target(Document page) {
            this.page = page;
            page.addStyleSheet(new StyleSheet()
                    .rule("body", s -> s.set(CssProperty.PADDING, "22px 26px").set(CssProperty.FONT_SIZE, "14px"))
                    .rule("h1", s -> s.set(CssProperty.MARGIN, "0 0 2px").set(CssProperty.FONT_SIZE, "26px"))
                    .rule("dl", s -> s.set(CssProperty.DISPLAY, "grid").set(CssProperty.GRID_TEMPLATE_COLUMNS, "170px 1fr").set(CssProperty.GAP, "6px 14px"))
                    .rule("dt", s -> s.set(CssProperty.OPACITY, ".6"))
                    .rule("dd", s -> s.set(CssProperty.MARGIN, "0").set(CssProperty.FONT_FAMILY, "ui-monospace, monospace"))
                    .rule(".on", s -> s.set(CssProperty.COLOR, Style.lightDark("#0a7a3d", "#5fd38d")).set(CssProperty.FONT_WEIGHT, "600"))
                    .rule(".last", s -> s.set(CssProperty.PADDING, "12px 14px").set(CssProperty.BORDER_RADIUS, "8px")
                            .set(CssProperty.BACKGROUND, Style.lightDark("#eef3ff", "#222a3a")).set(CssProperty.WHITE_SPACE, "pre-wrap")
                            .set(CssProperty.FONT_SIZE, "12px").set(CssProperty.MIN_HEIGHT, "40px"))
                    .rule(".drop", s -> s.set(CssProperty.MARGIN_TOP, "14px").set(CssProperty.PADDING, "18px").set(CssProperty.TEXT_ALIGN, "center")
                            .set(CssProperty.BORDER, "2px dashed " + Style.var("line")).set(CssProperty.BORDER_RADIUS, "10px")));
            Dl facts = Html.dl(Html.dt("Inner size"), size, Html.dt("Position"), position, Html.dt("Shown"), shown, Html.dt("Focused"), focused,
                    Html.dt("Zoom (device pixel ratio)"), zoom, Html.dt("Light or dark"), scheme, Html.dt("Last shortcut"), shortcut,
                    Html.dt("Fullscreen"), Html.dd("Escape leaves it"));
            A newWindow = Html.a("open in a new window").setHref("https://example.com/").setTarget(A.Target.BLANK);
            A download = Html.a("download a file").setHref("data:text/plain,Downloaded%20by%20swaswa").setAttribute("download", "swaswa.txt");
            page.body().add(Html.h1("Target window"), Html.p("The control panel changes this window. What it is now:").setOpacity(.65), facts,
                    Html.p(Html.text("Links: "), newWindow, Html.text(" (Java is told) · "), download),
                    Html.div("Last event:"), last.cssClass("last"), drop.cssClass("drop"));
            measure();
        }

        /** What only the page knows: its size, its zoom, its colour scheme. */
        void measure() {
            Object m = page.view().eval("[innerWidth, innerHeight, devicePixelRatio, matchMedia('(prefers-color-scheme: dark)').matches]").join();
            if (m instanceof java.util.List<?> l && l.size() == 4) {
                size.setText(((Number) l.get(0)).intValue() + " × " + ((Number) l.get(1)).intValue());
                zoom.setText(String.format("%.2f", ((Number) l.get(2)).doubleValue()));
                scheme.setText(Boolean.TRUE.equals(l.get(3)) ? "dark" : "light");
            }
        }

        void told(WebViewEvent event) {
            last.setText(event.getClass().getSimpleName() + "\n" + event);
            switch (event) {
                case Moved m -> position.setText((int) m.x() + ", " + (int) m.y());
                case Focused f -> on(focused, f.focused() ? "yes" : "no", f.focused());
                case WindowState w -> {
                    java.util.List<String> on = new java.util.ArrayList<>();
                    if (w.minimized()) on.add("minimized");
                    if (w.maximized()) on.add("maximized");
                    if (w.fullscreen()) on.add("fullscreen");
                    on(shown, on.isEmpty() ? "normal" : String.join(", ", on), !on.isEmpty());
                }
                case Shortcut s -> on(shortcut, s.shortcut(), true);
                case FileDrop f -> drop.setText(f.toString());
                default -> { }
            }
            if (event instanceof Resized || event instanceof ScaleChanged || event instanceof SystemThemeChanged) {
                try {
                    measure();
                } catch (RuntimeException closing) {
                    // the window went while it was measured
                }
            }
        }

        private static void on(Dd field, String text, boolean on) {
            field.setText(text);
            if (on) field.cssClass("on");
            else field.removeCssClass("on");
        }
    }
}
