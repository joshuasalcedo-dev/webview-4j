package io.joshuasalcedo.swaswa.ffi;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Everything swaswa-ffi does, in two windows: a control panel, whose buttons call Java ({@code showcase} command), and
 * a target window beside it, which Java changes with {@link App} and {@link View} and which shows its own state as it
 * changes. Every event the native side reports is listed in the panel as it comes. Not part of the jar (a test
 * source). {@code just ffi-showcase}.
 */
final class FfiShowcase {

    private FfiShowcase() {}

    /** The control panel. */
    private static volatile View main;
    /** The window the panel changes. */
    private static volatile View target;
    private static final Map<String, Boolean> toggles = new java.util.concurrent.ConcurrentHashMap<>();
    /** A host window's own UI around embedded views: how its views are laid out at a new size (host id -> layout). */
    private static final Map<String, java.util.function.BiConsumer<Double, Double>> layouts = new java.util.concurrent.ConcurrentHashMap<>();
    /** The browser windows' embedded page (host id -> the embedded view). */
    private static final Map<String, View> browsers = new java.util.concurrent.ConcurrentHashMap<>();
    /** Each embedded page's host (embedded id -> host), to tell the host where its page is. */
    private static final Map<String, View> hostOf = new java.util.concurrent.ConcurrentHashMap<>();
    /** The Wikipedia window the injection buttons change. */
    private static volatile View injected;
    /** The toolbar of a browser window, and the header of the many-webviews window: logical pixels. */
    private static final double BAR = 46;

    public static void main(String[] args) {
        App.run(app -> {
            app.handle("showcase", call -> act(app, call));
            app.subscribe(WebViewEvent.class, FfiShowcase::show);
            main = app.open(WindowOptions.html(PAGE).title("swaswa-ffi: control panel").size(760, 940).position(40, 40)
                    .minSize(600, 500).devtools(true).capture(Capture.API)).join();
            target(app);
            app.subscribe(Resized.class, resized -> {
                var layout = layouts.get(resized.view());
                if (layout != null) layout.accept(resized.width(), resized.height());
            });
            app.subscribe(PageLoad.class, load -> {
                View host = hostOf.get(load.view());
                if (host != null) host.emit("browser.url", Map.of("url", load.url(), "state", load.state().name()));
            });
            app.subscribe(TitleChanged.class, title -> {
                View host = hostOf.get(title.view());
                if (host != null && browsers.containsKey(host.id())) host.setTitle(title.title());
            });
            app.subscribe(Closed.class, closed -> {
                layouts.remove(closed.view());
                browsers.remove(closed.view());
                hostOf.values().removeIf(h -> h.id().equals(closed.view()));
            });
            String id = main.id();
            app.subscribe(Closed.class, closed -> {
                if (closed.view().equals(id)) app.quit();
            });
        });
    }

    /** The target window: open, or opened again (it may have been closed). */
    private static synchronized View target(App app) {
        View t = target;
        if (t != null && app.views().join().stream().anyMatch(v -> v.id().equals(t.id()))) return t;
        target = app.open(WindowOptions.html(TARGET).title("Target window").size(720, 560).position(820, 60).devtools(true)
                .fileDrop(true).newWindows(WindowOptions.NewWindows.JAVA).shortcuts("Mod+S", "F5", "Mod+Shift+P")).join();
        toggles.clear();
        return target;
    }

    /** One event: to the panel's log (the noisy ones left out), and the target's own to the target. */
    private static void show(WebViewEvent event) {
        View panel = main;
        if (panel == null || event instanceof LogRecord log && log.source() == LogRecord.Source.NATIVE) return;
        if (event instanceof PageCall call && "showcase".equals(call.name())) return; // the buttons' own calls
        if (event.view() != null && event.view().equals(panel.id()) && (event instanceof Moved || event instanceof Resized)) return;
        panel.emit("showcase.event", Map.of("kind", event.getClass().getSimpleName(), "text", event.toString()));
        View t = target;
        if (t != null && t.id().equals(event.view())) {
            t.emit("target.event", Map.of("kind", event.getClass().getSimpleName(), "text", event.toString()));
        }
    }

    private static boolean flip(String name) {
        boolean now = !toggles.getOrDefault(name, false);
        toggles.put(name, now);
        return now;
    }

    /** What a button asked: done, and what to say about it. */
    private static Object act(App app, Invocation call) throws Exception {
        String action = call.text("action");
        // a button's value, when it has one (most have none: null)
        String arg = call.args() instanceof Map<?, ?> args && args.get("arg") != null ? String.valueOf(args.get("arg")) : null;
        switch (action) {
            // ---- every window of the app ----
            case "windows": return windows(app);
            case "win-focus": return done(app.view(arg).focus(), "focused " + arg);
            case "win-minimize": return done(app.view(arg).minimize(), "minimized " + arg);
            case "win-restore": return done(app.view(arg).restore(), "restored " + arg);
            case "win-close": return done(app.view(arg).close(), "closed " + arg);
            case "arrange": return arrange(app, arg);
            // ---- webviews ----
            case "site": return opened(app.open(WindowOptions.url(arg).title(arg).size(1100, 780)));
            case "browser": return browser(app, arg);
            case "multi": return multi(app);
            case "browser-nav": return browserNav(call.view(), arg);
            // ---- injecting into a site that is not ours ----
            case "inject-open": return opened(CompletableFuture.completedFuture(injectedWindow(app, true)));
            case "inject-css": return injectCss(app, arg);
            case "inject-script": return injectScript(app);
            default: break;
        }
        View view = target(app);
        return switch (action) {
            // ---- the window ----
            case "minimize" -> done(view.minimize(), "minimized");
            case "maximize" -> done(view.maximize(), "maximized");
            case "restore" -> done(view.restore(), "restored");
            case "fullscreen" -> { boolean on = flip("fullscreen"); yield done(view.setFullscreen(on), "fullscreen " + on); }
            case "center" -> done(view.center(), "centered on its monitor");
            case "on-top" -> { boolean on = flip("onTop"); yield done(view.setAlwaysOnTop(on), "always on top " + on); }
            case "resizable" -> { boolean off = flip("notResizable"); yield done(view.setResizable(!off), "resizable " + !off); }
            case "min-size" -> done(view.setMinSize(600, 400), "min size 600 x 400");
            case "max-size" -> done(view.setMaxSize(1400, 1000), "max size 1400 x 1000");
            case "no-max-size" -> done(view.clearMaxSize(), "no max size");
            case "minimizable" -> { boolean off = flip("notMinimizable"); yield done(view.setMinimizable(!off), "minimize button " + !off); }
            case "maximizable" -> { boolean off = flip("notMaximizable"); yield done(view.setMaximizable(!off), "maximize button " + !off); }
            case "closable" -> { boolean off = flip("notClosable"); yield done(view.setClosable(!off), "close button " + !off); }
            case "skip-taskbar" -> { boolean on = flip("skipTaskbar"); yield done(view.setSkipTaskbar(on), "no taskbar entry " + on); }
            case "protect" -> { boolean on = flip("protect"); yield done(view.setContentProtection(on), "hidden from screen capture " + on); }
            case "decorations" -> { boolean off = flip("noFrame"); yield done(view.setDecorations(!off), "frame and title bar " + !off
                    + (off ? " (resize it from its edges; the title bar is gone)" : "")); }
            case "title" -> done(view.setTitle(arg == null || arg.isBlank() ? "swaswa-ffi" : arg), "title: " + arg);
            case "theme" -> done(view.setTheme(Theme.valueOf(arg)), "theme " + arg);
            case "progress" -> done(view.setProgress(View.Progress.valueOf(arg), "NORMAL".equals(arg) || "PAUSED".equals(arg) || "ERROR".equals(arg) ? 40 : -1), "taskbar progress " + arg);
            case "badge" -> done(view.setBadge(Long.parseLong(arg)), "badge " + arg + " (the macOS dock; Windows to come)");
            case "attention" -> {
                // after a moment: the window must not have the focus for the taskbar to flash
                CompletableFuture.delayedExecutor(3, java.util.concurrent.TimeUnit.SECONDS)
                        .execute(() -> view.requestAttention(View.Attention.CRITICAL));
                yield "in 3 seconds: switch to another window to see the taskbar flash";
            }
            case "target" -> { view.focus().join(); yield "the target window, in front"; }
            case "geometry" -> view.geometry().join().toString();
            case "monitors" -> view.monitors().join().toString();
            // ---- other windows ----
            case "frameless" -> opened(app.open(WindowOptions.html(FRAMELESS).title("Frameless").size(520, 340).decorations(false).shadow(true)));
            case "owned" -> opened(app.open(WindowOptions.html(page("An owned window", "It stays above the showcase, minimizes with it, and has no taskbar entry of its own."))
                    .title("Owned").size(420, 260).owner(view)));
            case "embedded" -> opened(view.embed(WindowOptions.html(page("An embedded view",
                    "A second webview inside the showcase's window: its own page, placed by Java.")), 760, 600, 460, 220));
            case "transparent" -> opened(app.open(WindowOptions.html(TRANSPARENT).title("Transparent").size(320, 320)
                    .decorations(false).transparent(true).alwaysOnTop(true).skipTaskbar(true).shadow(false)));
            case "backdrop" -> opened(app.open(WindowOptions.html(BACKDROP.replace("$MATERIAL", arg)).title(arg + " backdrop").size(480, 320)
                    .backdrop(WindowOptions.Backdrop.valueOf(arg)).theme(Theme.DARK)));
            case "palette" -> opened(app.open(WindowOptions.html(page("A palette", "It does not take the focus, and is left out of screen capture."))
                    .title("Palette").size(360, 220).focusable(false).contentProtection(true).alwaysOnTop(true).owner(view)));
            // ---- the page ----
            case "zoom" -> { double z = Double.parseDouble(arg); yield done(view.setZoom(z), "zoom " + (int) (z * 100) + "%"); }
            case "reload" -> { view.reload(); yield "reloading"; }
            case "print" -> done(view.print(), "the print dialog");
            case "devtools" -> done(view.openDevTools(), "developer tools open");
            case "clear-data" -> done(view.clearBrowsingData(), "cookies, storage and cache cleared");
            case "user-agent" -> String.valueOf(view.eval("navigator.userAgent").join());
            case "screenshot" -> {
                byte[] png = view.screenshot(0.5).join();
                Path file = Files.createTempFile("swaswa-screenshot-", ".png");
                Files.write(file, png);
                yield Map.of("text", "screenshot " + png.length + " bytes: " + file, "image", "data:image/png;base64," + Base64.getEncoder().encodeToString(png));
            }
            case "cookie" -> {
                view.setCookie(new Cookie("showcase", "hello", "", "/", false, false)).join();
                yield view.allCookies().join().toString();
            }
            case "storage" -> {
                view.localStorage().set("showcase", "kept in localStorage").join();
                yield "localStorage: " + view.localStorage().get("showcase").join();
            }
            case "shortcuts-off" -> done(view.setShortcuts(), "the window's shortcuts off");
            case "shortcuts-on" -> done(view.setShortcuts("Mod+S", "F5", "Mod+Shift+P"), "Mod+S, F5, Mod+Shift+P");
            // ---- the system ----
            case "open-file" -> String.valueOf(app.openFile(new FileDialog().title("Open a file").parent(view)).join().orElse(null));
            case "save-file" -> String.valueOf(app.saveFile(new FileDialog().title("Save as").fileName("notes.txt").parent(view)).join().orElse(null));
            case "pick-folder" -> String.valueOf(app.pickFolder(new FileDialog().title("Pick a folder").parent(view)).join().orElse(null));
            case "message" -> String.valueOf(app.message(MessageDialog.of("swaswa-ffi", "A native message box.").parent(view)).join());
            case "confirm" -> "confirmed: " + app.confirm("swaswa-ffi", "Is this a native dialog?").join();
            case "secret" -> {
                SecretStore store = SecretStore.of("swaswa-showcase");
                store.put("demo", "s3cret at " + java.time.LocalTime.now().withNano(0));
                String back = store.get("demo").orElse("(none)");
                store.delete("demo");
                yield "the system's vault kept and gave back: " + back;
            }
            case "grammar" -> {
                String text = arg == null ? "" : arg;
                var mistakes = Grammar.english().check(text);
                yield mistakes.isEmpty() ? "no mistakes" : mistakes.stream().map(m -> m.found(text) + ": " + m.message()
                        + (m.suggestions().isEmpty() ? "" : " → " + m.suggestions().getFirst())).toList().toString();
            }
            default -> throw new IllegalArgumentException("no action " + action);
        };
    }

    /** Every window and view of the app: where it is and how it is shown (an embedded view has no window of its own). */
    private static Object windows(App app) {
        java.util.List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (ViewInfo info : app.views().join()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", info.id());
            row.put("title", info.title());
            row.put("url", info.url());
            row.put("panel", info.id().equals(main.id()));
            try {
                Geometry g = app.view(info.id()).geometry().join();
                row.put("where", (int) g.x() + ", " + (int) g.y());
                row.put("size", (int) g.width() + " × " + (int) g.height());
                row.put("state", g.minimized() ? "minimized" : g.fullscreen() ? "fullscreen" : g.maximized() ? "maximized" : g.visible() ? "normal" : "hidden");
                row.put("focused", g.focused());
            } catch (RuntimeException embedded) {
                View host = hostOf.get(info.id());
                row.put("where", "embedded" + (host == null ? "" : " in " + host.id()));
                row.put("size", "");
                row.put("state", "embedded");
                row.put("focused", false);
            }
            out.add(row);
        }
        return out;
    }

    /** The app's windows, arranged on the main monitor: side by side, in a grid, or cascading. */
    private static String arrange(App app, String how) {
        java.util.List<View> windows = new java.util.ArrayList<>();
        for (ViewInfo info : app.views().join()) {
            View v = app.view(info.id());
            try {
                Geometry g = v.geometry().join();
                if (g.visible()) windows.add(v);
            } catch (RuntimeException embedded) {
                // embedded views move with their window
            }
        }
        Monitor monitor = main.monitors().join().stream().filter(Monitor::primary).findFirst().orElseThrow();
        double scale = monitor.scaleFactor();
        // the work area, roughly: the taskbar's height left free
        double left = monitor.x(), top = monitor.y(), width = monitor.width(), height = monitor.height() - 48;
        int n = windows.size();
        for (int i = 0; i < n; i++) {
            View v = windows.get(i);
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

    /**
     * The Wikipedia window: opened with an initialization script, which runs on every page it loads before the page's
     * own scripts (a small badge, kept across navigation).
     */
    private static synchronized View injectedWindow(App app, boolean fresh) {
        View w = injected;
        if (!fresh && w != null && app.views().join().stream().anyMatch(v -> v.id().equals(w.id()))) return w;
        if (w != null) w.close();
        injected = app.open(WindowOptions.url("https://en.wikipedia.org/wiki/Java_(programming_language)")
                .title("Wikipedia, injected by swaswa").size(1100, 820).initializationScript(INIT_SCRIPT)).join();
        return injected;
    }

    /** CSS put into the page as it is now (a {@code <style>} of its own, replaced each time). */
    private static String injectCss(App app, String which) {
        String css = switch (which) {
            case "SEPIA" -> "html { filter: sepia(.55) contrast(1.05); } body, .vector-body { font-family: Georgia, serif !important; }";
            case "FOCUS" -> "#vector-main-menu-pinned-container, .vector-column-start, .vector-column-end, #mw-panel, .mw-footer,"
                    + " .vector-header-container, .vector-sticky-header { display: none !important; }"
                    + " .mw-page-container { max-width: 760px !important; margin: 0 auto !important; }";
            case "LINKS" -> "a { background: #fff3a8 !important; color: #8a3b00 !important; border-radius: 3px; padding: 0 2px; }";
            default -> "";
        };
        String script = """
                (() => { let s = document.getElementById('swaswa-injected-css');
                  if (!s) { s = document.createElement('style'); s.id = 'swaswa-injected-css'; document.head.append(s); }
                  s.textContent = %s; return s.textContent.length; })()""".formatted(Json.write(css));
        Object length = injectedWindow(app, false).eval(script).join();
        return css.isEmpty() ? "the injected CSS taken out" : "injected " + length + " characters of CSS: " + which.toLowerCase();
    }

    /** A script run in the page as it is now: it changes the page, and what it returns comes back to Java. */
    private static Object injectScript(App app) {
        Object found = injectedWindow(app, false).eval("""
                (() => {
                  let b = document.getElementById('swaswa-banner');
                  if (!b) {
                    b = document.createElement('div'); b.id = 'swaswa-banner';
                    Object.assign(b.style, { position: 'fixed', left: '50%', top: '14px', transform: 'translateX(-50%)', zIndex: 99999,
                      background: '#3157d5', color: 'white', padding: '10px 18px', borderRadius: '999px', font: '600 14px system-ui',
                      boxShadow: '0 6px 24px #0004' });
                    document.body.append(b);
                  }
                  const links = document.querySelectorAll('#mw-content-text a[href^="/wiki/"]').length;
                  const title = document.querySelector('#firstHeading')?.textContent ?? document.title;
                  b.textContent = 'Injected by swaswa: ' + links + ' links in "' + title + '"';
                  return { title, links, url: location.href };
                })()""").join();
        return "the page answered Java: " + found;
    }

    /** A window with the app's own browser UI around an external site, which is an embedded webview under it. */
    private static String browser(App app, String url) {
        View host = app.open(WindowOptions.html(BROWSER.replace("$URL", url)).title(url).size(1100, 780)).join();
        Geometry g = host.geometry().join();
        View page = host.embed(WindowOptions.url(url), 0, BAR, g.width(), g.height() - BAR).join();
        browsers.put(host.id(), page);
        hostOf.put(page.id(), host);
        layouts.put(host.id(), (w, h) -> page.place(0, BAR, w, Math.max(1, h - BAR)));
        return "a browser window of our own (" + host.id() + ") around " + url + " (" + page.id() + ")";
    }

    private static String browserNav(View host, String what) {
        View page = browsers.get(host.id());
        if (page == null) return "no page";
        switch (what) {
            case "back" -> page.back();
            case "forward" -> page.forward();
            case "reload" -> page.reload();
            default -> {
                String url = what.startsWith("go:") ? what.substring(3).strip() : what;
                if (!url.contains("://")) url = "https://" + url;
                page.navigate(url);
            }
        }
        return what;
    }

    /** One window, several webviews: external sites and a page of our own, side by side under the window's own header. */
    private static String multi(App app) {
        String[][] sites = {
                {"YouTube", "https://www.youtube.com/"},
                {"Wikipedia", "https://en.wikipedia.org/wiki/WebView"},
                {"example.com", "https://example.com/"},
                {"swaswa on GitHub", "https://github.com/joshuasalcedo-dev/swaswa"},
                {"Our own page", null},
        };
        View host = app.open(WindowOptions.html(MULTI).title("Several webviews, one window").size(1500, 940)).join();
        Geometry g = host.geometry().join();
        java.util.List<View> views = new java.util.ArrayList<>();
        for (String[] site : sites) {
            WindowOptions options = site[1] == null
                    ? WindowOptions.html(page("Our own page", "An HTML page of the app's own, in the same window as four external sites: five webviews, one window."))
                    : WindowOptions.url(site[1]);
            View v = host.embed(options, 0, BAR, 100, 100).join();
            views.add(v);
            hostOf.put(v.id(), host);
        }
        int cols = 3, rows = 2;
        double gap = 8, caption = 22;
        java.util.function.BiConsumer<Double, Double> layout = (w, h) -> {
            double cellW = (w - gap * (cols + 1)) / cols, cellH = (h - BAR - gap * (rows + 1)) / rows;
            java.util.List<Map<String, Object>> cells = new java.util.ArrayList<>();
            for (int i = 0; i < views.size(); i++) {
                double x = gap + (i % cols) * (cellW + gap), y = BAR + gap + (i / cols) * (cellH + gap);
                views.get(i).place(x, y + caption, cellW, Math.max(1, cellH - caption));
                cells.add(Map.of("x", x, "y", y, "width", cellW, "label", sites[i][0] + (sites[i][1] == null ? "" : "  ·  " + sites[i][1])));
            }
            host.emit("multi.layout", cells);
        };
        layouts.put(host.id(), layout);
        layout.accept(g.width(), g.height());
        return "one window (" + host.id() + "), " + views.size() + " webviews";
    }

    private static String done(CompletableFuture<?> future, String said) {
        future.join();
        return said;
    }

    private static String opened(CompletableFuture<View> view) {
        return "opened " + view.join().id();
    }

    private static String page(String title, String text) {
        return """
                <!doctype html><meta charset=utf-8>
                <body style="font: 14px system-ui; margin: 24px; color: light-dark(#111, #eee); background: light-dark(#fff, #1e1e1e); color-scheme: light dark">
                <h2 style="margin: 0 0 8px">%s</h2><p>%s</p></body>""".formatted(title, text);
    }

    /** A window without the system's frame: its own title bar drags it, its buttons work, its edges resize it. */
    private static final String FRAMELESS = """
            <!doctype html><meta charset=utf-8>
            <style>
              body { margin: 0; font: 14px system-ui; color-scheme: light dark; background: light-dark(#fff, #1e1e1e); height: 100vh; display: flex; flex-direction: column }
              header { display: flex; align-items: center; gap: 8px; padding: 0 0 0 12px; height: 36px; background: light-dark(#e8eef8, #2a3240); user-select: none }
              header span { flex: 1 }
              header button { border: 0; background: transparent; width: 44px; height: 36px; font-size: 14px; cursor: default; color: inherit }
              header button:hover { background: light-dark(#0001, #fff2) }
              header button.close:hover { background: #c42b1c; color: white }
              main { padding: 16px }
            </style>
            <header id=bar><span>My own title bar</span>
              <button onclick="__webview4j.window.minimize()">&#xE921;—</button>
              <button onclick="__webview4j.window.toggleMaximize()">☐</button>
              <button class=close onclick="__webview4j.window.close()">✕</button></header>
            <main><p>No system frame. Drag the bar to move it, double-click it to maximize, and resize it from any edge or
              corner: the bridge does that for a frameless window.</p></main>
            <script>
              const bar = document.getElementById('bar');
              bar.addEventListener('mousedown', e => { if (e.target === bar || e.target.tagName === 'SPAN') __webview4j.window.startDrag(); });
              bar.addEventListener('dblclick', e => { if (e.target.tagName !== 'BUTTON') __webview4j.window.toggleMaximize(); });
            </script>""";

    /** A see-through, round window: only the page's own shape shows. */
    private static final String TRANSPARENT = """
            <!doctype html><meta charset=utf-8>
            <body style="margin:0; background: transparent; height: 100vh; display: grid; place-items: center">
            <div id=c style="width: 280px; height: 280px; border-radius: 50%; background: radial-gradient(circle at 30% 30%, #8ab4ff, #3157d5);
              color: white; font: 600 16px system-ui; display: grid; place-items: center; text-align: center; cursor: move">
              A transparent window<br>(drag me; double-click closes)</div>
            <script>
              const c = document.getElementById('c');
              c.addEventListener('mousedown', () => __webview4j.window.startDrag());
              c.addEventListener('dblclick', () => __webview4j.window.close());
            </script>""";

    /** Windows 11's material behind the window: the page is transparent where it should show. */
    private static final String BACKDROP = """
            <!doctype html><meta charset=utf-8>
            <body style="margin: 0; background: transparent; color: white; font: 15px system-ui; padding: 24px">
            <h2 style="margin: 0 0 8px">$MATERIAL</h2>
            <p>The page's background is transparent: Windows 11 (22H2 or later) draws its material behind it. On older
            Windows, or elsewhere, nothing.</p></body>""";

    /** Given to the Wikipedia window when it opens: runs on every page before the page's own scripts. */
    private static final String INIT_SCRIPT = """
            addEventListener('DOMContentLoaded', () => {
              const badge = document.createElement('div');
              badge.textContent = 'swaswa initialization script';
              Object.assign(badge.style, { position: 'fixed', right: '12px', bottom: '12px', zIndex: 99999, background: '#111d',
                color: '#9fe3b4', padding: '6px 10px', borderRadius: '8px', font: '12px ui-monospace, monospace' });
              document.body.append(badge);
            });""";

    /** A browser of the app's own: its toolbar here, the site in an embedded webview under it. */
    private static final String BROWSER = """
            <!doctype html><meta charset=utf-8>
            <style>
              :root { color-scheme: light dark; font: 13px system-ui }
              body { margin: 0; background: light-dark(#eef1f6, #202228) }
              nav { height: 46px; box-sizing: border-box; display: flex; align-items: center; gap: 6px; padding: 0 10px;
                border-bottom: 1px solid light-dark(#d6dbe4, #333); }
              button { width: 32px; height: 30px; border-radius: 6px; border: 0; background: transparent; font-size: 15px; cursor: pointer; color: inherit }
              button:hover { background: light-dark(#dde3ee, #31343c) }
              input { flex: 1; height: 30px; padding: 0 12px; border-radius: 15px; border: 1px solid light-dark(#cfd5e0, #3a3d45);
                background: light-dark(#fff, #16171a); color: inherit; font: inherit }
              #state { width: 70px; opacity: .6; font-size: 12px }
              .brand { font-weight: 600; padding-right: 6px }
            </style>
            <nav><span class=brand>swaswa</span>
              <button title=Back onclick="go('back')">◀</button><button title=Forward onclick="go('forward')">▶</button>
              <button title=Reload onclick="go('reload')">⟳</button>
              <input id=url value="$URL" onkeydown="if (event.key === 'Enter') go('go:' + this.value)">
              <span id=state></span></nav>
            <script>
              const go = (what) => __webview4j.invoke('showcase', { action: 'browser-nav', arg: what });
              __webview4j.listen('browser.url', (e) => {
                if (document.activeElement !== document.getElementById('url')) document.getElementById('url').value = e.url;
                document.getElementById('state').textContent = e.state === 'STARTED' ? 'loading…' : '';
              });
            </script>""";

    /** One window, several webviews: the header and each one's caption here; the webviews over this page. */
    private static final String MULTI = """
            <!doctype html><meta charset=utf-8>
            <style>
              :root { color-scheme: light dark; font: 13px system-ui }
              body { margin: 0; background: light-dark(#e9edf3, #15161a) }
              header { height: 46px; box-sizing: border-box; display: flex; align-items: center; padding: 0 16px; gap: 10px;
                background: light-dark(#fff, #1f2126); border-bottom: 1px solid light-dark(#d6dbe4, #2c2f36) }
              header b { font-size: 15px } header span { opacity: .6 }
              .cap { position: absolute; height: 22px; line-height: 22px; font-size: 12px; font-weight: 600; padding: 0 8px;
                overflow: hidden; white-space: nowrap; text-overflow: ellipsis; background: light-dark(#dfe5ef, #262930); border-radius: 6px 6px 0 0 }
            </style>
            <header><b>Several webviews, one window</b><span>Each pane is its own webview, placed by Java; resize the window and they follow.</span></header>
            <div id=caps></div>
            <script>
              __webview4j.listen('multi.layout', (cells) => {
                const caps = document.getElementById('caps'); caps.textContent = '';
                for (const c of cells) {
                  const d = document.createElement('div'); d.className = 'cap'; d.textContent = c.label;
                  Object.assign(d.style, { left: c.x + 'px', top: c.y + 'px', width: c.width + 'px', boxSizing: 'border-box' });
                  caps.append(d);
                }
              });
            </script>""";

    /** The target: what it is now, as the panel changes it. */
    private static final String TARGET = """
            <!doctype html><meta charset=utf-8>
            <title>Target window</title>
            <style>
              :root { color-scheme: light dark; font: 14px system-ui }
              body { margin: 0; padding: 22px 26px; background: light-dark(#fbfcfe, #17181b); color: light-dark(#111, #eee) }
              h1 { margin: 0 0 2px; font-size: 26px } p.lead { margin: 0 0 16px; opacity: .65 }
              dl { display: grid; grid-template-columns: 170px 1fr; gap: 6px 14px; margin: 0 0 16px }
              dt { opacity: .6 } dd { margin: 0; font-family: ui-monospace, monospace }
              .on { color: light-dark(#0a7a3d, #5fd38d); font-weight: 600 }
              #last { padding: 12px 14px; border-radius: 8px; background: light-dark(#eef3ff, #222a3a); font-family: ui-monospace, monospace;
                font-size: 12px; white-space: pre-wrap; min-height: 40px }
              a { color: light-dark(#2457d6, #8ab4ff) }
              #drop { margin-top: 14px; padding: 18px; border: 2px dashed light-dark(#c9cfdb, #3a3f4a); border-radius: 10px; text-align: center; opacity: .75 }
            </style>
            <h1>Target window</h1>
            <p class=lead>The control panel changes this window. What it is now:</p>
            <dl>
              <dt>Inner size</dt><dd id=size></dd>
              <dt>Position</dt><dd id=pos>—</dd>
              <dt>Shown</dt><dd id=state>normal</dd>
              <dt>Focused</dt><dd id=focus>—</dd>
              <dt>Zoom (device pixel ratio)</dt><dd id=zoom></dd>
              <dt>Light or dark</dt><dd id=theme></dd>
              <dt>Last shortcut</dt><dd id=key>— (press Ctrl+S, F5 or Ctrl+Shift+P here)</dd>
              <dt>Fullscreen</dt><dd>Escape leaves it</dd>
            </dl>
            <p>Links: <a href="https://example.com/" target=_blank>open in a new window</a> (Java is told) ·
              <a href="data:text/plain,Downloaded%20by%20swaswa" download="swaswa.txt">download a file</a></p>
            <div>Last event:</div>
            <div id=last>—</div>
            <div id=drop>Drop a file on this window</div>
            <script>
              const $ = (id) => document.getElementById(id);
              const dark = matchMedia('(prefers-color-scheme: dark)');
              function measure() {
                $('size').textContent = innerWidth + ' × ' + innerHeight;
                $('zoom').textContent = devicePixelRatio.toFixed(2);
                $('theme').textContent = dark.matches ? 'dark' : 'light';
              }
              measure(); addEventListener('resize', measure); dark.addEventListener('change', measure);
              __webview4j.listen('target.event', (e) => {
                $('last').textContent = e.kind + '\n' + e.text;
                const field = (name) => (e.text.match(new RegExp(name + '=([^,\\]]+)')) || [])[1];
                if (e.kind === 'Moved') $('pos').textContent = field('x') + ', ' + field('y');
                if (e.kind === 'Focused') { const f = field('focused'); $('focus').innerHTML = f === 'true' ? '<span class=on>yes</span>' : 'no'; }
                if (e.kind === 'WindowState') {
                  const on = ['minimized', 'maximized', 'fullscreen'].filter((k) => field(k) === 'true');
                  $('state').innerHTML = on.length ? '<span class=on>' + on.join(', ') + '</span>' : 'normal';
                }
                if (e.kind === 'Shortcut') $('key').innerHTML = '<span class=on>' + field('shortcut') + '</span>';
                if (e.kind === 'FileDrop') $('drop').textContent = e.text;
                measure();
              });
            </script>""";

    /** The control panel: one button per feature, and the events as they come. */
    private static final String PAGE = """
            <!doctype html><meta charset=utf-8>
            <title>swaswa-ffi</title>
            <style>
              :root { color-scheme: light dark; font: 13px system-ui }
              body { margin: 0; display: grid; grid-template-rows: 1fr 300px; height: 100vh; background: light-dark(#f6f7f9, #18191c); color: light-dark(#111, #e8e8e8) }
              main { overflow: auto; padding: 16px 18px }
              aside { display: flex; flex-direction: column; min-height: 0; border-top: 1px solid light-dark(#dde, #333); background: light-dark(#fff, #1f2024) }
              h1 { font-size: 20px; margin: 0 0 4px } p.lead { margin: 0 0 14px; opacity: .7 }
              section { background: light-dark(#fff, #222327); border: 1px solid light-dark(#e3e5ea, #333); border-radius: 8px; padding: 12px 14px; margin-bottom: 12px }
              h2 { font-size: 13px; text-transform: uppercase; letter-spacing: .06em; opacity: .65; margin: 0 0 8px }
              .row { display: flex; flex-wrap: wrap; gap: 6px; align-items: center }
              button, input, select { font: inherit; color: inherit }
              button { padding: 5px 10px; border-radius: 6px; border: 1px solid light-dark(#cfd3da, #444); background: light-dark(#fafbfc, #2b2c31); cursor: pointer }
              button:hover { background: light-dark(#eef1f6, #34363c) }
              input, select { padding: 4px 8px; border-radius: 6px; border: 1px solid light-dark(#cfd3da, #444); background: light-dark(#fff, #1a1b1e) }
              #said { padding: 10px 14px; border-bottom: 1px solid light-dark(#dde, #333); min-height: 48px; white-space: pre-wrap; font-family: ui-monospace, monospace; font-size: 12px }
              #said img { display: block; max-width: 100%; margin-top: 6px; border: 1px solid #8884 }
              #log { flex: 1; overflow: auto; font: 11.5px ui-monospace, monospace; padding: 6px 10px }
              #log div { padding: 2px 0; border-bottom: 1px dashed light-dark(#eee, #2a2a2a) } #log b { color: light-dark(#3157d5, #8ab4ff) }
              .hint { opacity: .6; font-size: 12px }
              table { width: 100%; border-collapse: collapse; margin-top: 8px; font-size: 12px }
              td, th { text-align: left; padding: 4px 6px; border-bottom: 1px solid light-dark(#eceef2, #2c2d31); white-space: nowrap }
              th { font-weight: 600; opacity: .65 } td.t { max-width: 180px; overflow: hidden; text-overflow: ellipsis }
              td button { padding: 2px 6px; font-size: 11px }
            </style>
            <main>
              <h1>swaswa-ffi</h1>
              <p class=lead>Every button calls Java, which changes the <b>target window</b> with App and View (closed, it opens
                again). <button data-a=target>Bring the target window forward</button></p>

              <section><h2>The target window</h2><div class=row>
                <button data-a=minimize>Minimize</button><button data-a=maximize>Maximize</button><button data-a=restore>Restore</button>
                <button data-a=fullscreen>Fullscreen on/off</button><button data-a=center>Center</button><button data-a=on-top>Always on top on/off</button>
                <button data-a=resizable>Resizable on/off</button><button data-a=min-size>Min 600×400</button><button data-a=max-size>Max 1400×1000</button>
                <button data-a=no-max-size>No max</button></div>
                <div class=row style="margin-top:6px"><button data-a=minimizable>Minimize button on/off</button><button data-a=maximizable>Maximize button on/off</button>
                <button data-a=closable>Close button on/off</button><button data-a=skip-taskbar>Taskbar entry on/off</button><button data-a=protect>Hide from capture on/off</button>
                <button data-a=decorations>Frame and title bar on/off</button></div>
                <div class=row style="margin-top:6px"><input id=title placeholder="A title" value="swaswa-ffi"><button data-a=title data-from=title>Set title</button>
                <select id=theme><option>SYSTEM<option>LIGHT<option>DARK</select><button data-a=theme data-from=theme>Theme</button>
                <button data-a=geometry>Geometry</button><button data-a=monitors>Monitors</button></div></section>

              <section><h2>The target's taskbar button</h2><div class=row>
                <select id=progress><option>NORMAL<option>INDETERMINATE<option>PAUSED<option>ERROR<option>NONE</select><button data-a=progress data-from=progress>Progress</button>
                <button data-a=badge data-arg=3>Badge 3</button><button data-a=badge data-arg=0>No badge</button><button data-a=attention>Ask for attention</button></div></section>

              <section><h2>Other windows</h2><div class=row>
                <button data-a=frameless>Frameless, own title bar</button><button data-a=owned>Owned (child) window</button><button data-a=embedded>Embedded view</button>
                <button data-a=transparent>Transparent, round</button><button data-a=palette>Palette (no focus, no capture)</button></div>
                <div class=row style="margin-top:6px"><select id=material><option>MICA<option>ACRYLIC<option>TABBED</select><button data-a=backdrop data-from=material>Windows 11 backdrop</button>
                <span class=hint>Several webviews in one window: the embedded view; parent and child: the owned window.</span></div></section>

              <section><h2>Webviews</h2>
                <div class=row><span class=hint>An external site in a window of its own:</span>
                  <button data-a=site data-arg="https://example.com/">example.com</button>
                  <button data-a=site data-arg="https://en.wikipedia.org/wiki/WebView">Wikipedia</button>
                  <button data-a=site data-arg="https://github.com/joshuasalcedo-dev/swaswa">swaswa on GitHub</button></div>
                <div class=row style="margin-top:6px"><span class=hint>Our own UI around an external site (an embedded webview):</span>
                  <button data-a=browser data-arg="https://en.wikipedia.org/wiki/WebView">Browser: Wikipedia</button>
                  <button data-a=browser data-arg="https://www.youtube.com/">Browser: YouTube</button></div>
                <div class=row style="margin-top:6px"><span class=hint>One window, several webviews (YouTube, Wikipedia, example.com, GitHub, our own page):</span>
                  <button data-a=multi>Several webviews, one window</button></div></section>

              <section><h2>Injecting into a site (Wikipedia)</h2>
                <div class=row><button data-a=inject-open>Open Wikipedia (with an initialization script)</button>
                  <span class=hint>a badge on every page it loads, put in before the page's own scripts</span></div>
                <div class=row style="margin-top:6px"><span class=hint>Inject CSS:</span>
                  <button data-a=inject-css data-arg=SEPIA>Sepia, serif</button><button data-a=inject-css data-arg=FOCUS>Hide the sidebars</button>
                  <button data-a=inject-css data-arg=LINKS>Highlight links</button><button data-a=inject-css data-arg=NONE>Take it out</button></div>
                <div class=row style="margin-top:6px"><button data-a=inject-script>Inject a script</button>
                  <span class=hint>it adds a banner, and what it returns (the title, its links) comes back to Java</span></div></section>

              <section><h2>Running windows</h2>
                <div class=row><span class=hint>Arrange them:</span><button data-a=arrange data-arg=TILE>Side by side</button>
                  <button data-a=arrange data-arg=GRID>Grid</button><button data-a=arrange data-arg=CASCADE>Cascade</button></div>
                <table id=windows></table></section>

              <section><h2>The target's page</h2><div class=row>
                <button data-a=zoom data-arg=0.8>Zoom 80%</button><button data-a=zoom data-arg=1>100%</button><button data-a=zoom data-arg=1.25>125%</button>
                <button data-a=reload>Reload</button><button data-a=print>Print</button><button data-a=devtools>Developer tools</button>
                <button data-a=user-agent>User agent</button><button data-a=screenshot>Screenshot</button><button data-a=cookie>Set a cookie</button>
                <button data-a=storage>localStorage</button><button data-a=clear-data>Clear browsing data</button></div>
                <div class=row style="margin-top:6px"><span class=hint>In the target: a new-window link (NewWindowRequested), a download (Download),
                  shortcuts Ctrl+S, F5, Ctrl+Shift+P (Shortcut), dropping a file (FileDrop).</span>
                <button data-a=shortcuts-off>Shortcuts off</button><button data-a=shortcuts-on>On</button></div></section>

              <section><h2>The system</h2><div class=row>
                <button data-a=open-file>Open file</button><button data-a=save-file>Save file</button><button data-a=pick-folder>Pick folder</button>
                <button data-a=message>Message box</button><button data-a=confirm>Confirm</button><button data-a=secret>Secret store</button></div>
                <div class=row style="margin-top:6px"><input id=text size=40 value="This are a sentence with a speling mistake."><button data-a=grammar data-from=text>Check spelling and grammar</button></div></section>
            </main>
            <aside><div id=said>Press a button.</div><div id=log></div></aside>
            <script>
              const said = document.getElementById('said'), log = document.getElementById('log');
              document.addEventListener('click', async (e) => {
                const b = e.target.closest('button[data-a]'); if (!b) return;
                const arg = b.dataset.from ? document.getElementById(b.dataset.from).value : (b.dataset.arg ?? null);
                said.textContent = '…';
                try {
                  const r = await __webview4j.invoke('showcase', { action: b.dataset.a, arg });
                  if (r && typeof r === 'object' && r.image) { said.textContent = r.text; const img = new Image(); img.src = r.image; said.append(img); }
                  else said.textContent = typeof r === 'string' ? r : JSON.stringify(r);
                } catch (err) { said.textContent = 'failed: ' + (err.message || err); }
              });
              // every window of the app, live
              async function refreshWindows() {
                let rows;
                try { rows = await __webview4j.invoke('showcase', { action: 'windows', arg: null }); } catch { return; }
                const t = document.getElementById('windows');
                t.innerHTML = '<tr><th>View<th>Title<th>Where<th>Size<th>State<th></tr>';
                for (const r of rows) {
                  const tr = document.createElement('tr');
                  const cells = [r.id + (r.panel ? ' (this panel)' : ''), r.title, r.where, r.size, r.state + (r.focused ? ' · focused' : '')];
                  for (const [i, c] of cells.entries()) { const td = document.createElement('td'); if (i === 1) td.className = 't'; td.textContent = c; td.title = c; tr.append(td); }
                  const act = document.createElement('td');
                  for (const [label, a] of [['Focus', 'win-focus'], ['Min', 'win-minimize'], ['Restore', 'win-restore'], ['Close', 'win-close']]) {
                    if (r.state === 'embedded' && a !== 'win-close') continue;
                    const b = document.createElement('button'); b.textContent = label; b.dataset.a = a; b.dataset.arg = r.id; act.append(b);
                  }
                  tr.append(act); t.append(tr);
                }
              }
              refreshWindows(); setInterval(refreshWindows, 1500);

              __webview4j.listen('showcase.event', (e) => {
                const d = document.createElement('div'); d.innerHTML = '<b></b> '; d.firstChild.textContent = e.kind;
                d.append(e.text.replace(/^[A-Za-z]+\\[/, '[')); log.prepend(d);
                while (log.childNodes.length > 300) log.lastChild.remove();
              });
            </script>""";
}
