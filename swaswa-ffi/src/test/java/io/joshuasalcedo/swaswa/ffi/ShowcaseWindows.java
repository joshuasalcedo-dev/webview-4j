package io.joshuasalcedo.swaswa.ffi;

import static io.joshuasalcedo.swaswa.ffi.ShowcaseUi.button;
import static io.joshuasalcedo.swaswa.ffi.ShowcaseUi.plain;

import io.joshuasalcedo.swaswa.component.AbsoluteLayout;
import io.joshuasalcedo.swaswa.component.BoxLayout;
import io.joshuasalcedo.swaswa.component.EventType;
import io.joshuasalcedo.swaswa.component.StackLayout;
import io.joshuasalcedo.swaswa.css.CssProperty;
import io.joshuasalcedo.swaswa.css.Style;
import io.joshuasalcedo.swaswa.css.StyleSheet;
import io.joshuasalcedo.swaswa.html.Button;
import io.joshuasalcedo.swaswa.html.Canvas;
import io.joshuasalcedo.swaswa.html.Div;
import io.joshuasalcedo.swaswa.html.Document;
import io.joshuasalcedo.swaswa.html.Header;
import io.joshuasalcedo.swaswa.html.Html;
import io.joshuasalcedo.swaswa.html.Input;
import io.joshuasalcedo.swaswa.html.Nav;
import io.joshuasalcedo.swaswa.html.P;
import io.joshuasalcedo.swaswa.html.Span;
import io.joshuasalcedo.swaswa.internal.Json;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/** The windows the control panel opens, each made of components. */
final class ShowcaseWindows {

    private ShowcaseWindows() {}

    /** A host window's own UI around embedded views: how its views are laid out at a new size (host id -> layout). */
    private static final Map<String, BiConsumer<Double, Double>> layouts = new ConcurrentHashMap<>();
    /** Each embedded page's browser window (embedded id -> its toolbar). */
    private static final Map<String, Browser> browsers = new ConcurrentHashMap<>();
    /** The toolbar of a browser window, and the header of the many-webviews window: CSS pixels. */
    private static final double BAR = 46;

    /** What the windows follow: their size (to lay out their views), their pages' loads and titles. */
    static void follow(App app) {
        app.subscribe(Resized.class, resized -> {
            var layout = layouts.get(resized.view());
            if (layout != null) layout.accept(resized.width(), resized.height());
        });
        app.subscribe(PageLoad.class, load -> {
            Browser b = browsers.get(load.view());
            if (b != null) b.loaded(load);
        });
        app.subscribe(TitleChanged.class, title -> {
            Browser b = browsers.get(title.view());
            if (b != null) b.host.view().setTitle(title.title());
        });
        app.subscribe(Closed.class, closed -> {
            layouts.remove(closed.view());
            browsers.remove(closed.view());
            browsers.values().removeIf(b -> b.host.view().id().equals(closed.view()));
        });
    }

    private static String opened(Document page) {
        return "opened " + page.view().id();
    }

    // ---- other windows ----

    /** A window without the system's frame: its own title bar (a drag region) moves it, its buttons work, its edges size it. */
    static String frameless(App app) {
        Document page = ShowcaseUi.window(app, WindowOptions.html("").title("Frameless").size(520, 340).decorations(false).shadow(true));
        View view = page.view();
        Header bar = Html.header(Html.span("My own title bar").style(CssProperty.FLEX, "1"),
                windowButton("—", "Minimize", () -> view.minimize()), windowButton("☐", "Maximize or restore", () -> toggleMaximize(view)),
                windowButton("✕", "Close", view::close).cssClass("close"));
        bar.setData("webview4j-drag-region", "").setLayout(BoxLayout.row(0)).setPadding("0 0 0 12px")
                .setPreferredSize(null, "36px").setBackground(Style.lightDark("#e8eef8", "#2a3240")).style(CssProperty.USER_SELECT, "none");
        bar.on(EventType.DBLCLICK, e -> {
            if (!(e.source() instanceof Button)) toggleMaximize(view);
        });
        page.addStyleSheet(new StyleSheet()
                .rule("header button", s -> s.set(CssProperty.BORDER, "0").set(CssProperty.BACKGROUND, "transparent").set(CssProperty.WIDTH, "44px")
                        .set(CssProperty.HEIGHT, "36px").set(CssProperty.BORDER_RADIUS, "0"))
                .rule("header button.close:hover", s -> s.set(CssProperty.BACKGROUND, "#c42b1c").set(CssProperty.COLOR, "white")));
        page.body().add(bar, Html.main(Html.p("No system frame. Drag the bar to move it, double-click it to maximize, and resize it from any"
                + " edge or corner: the bridge does that for a frameless window.")).setPadding("16px"));
        return opened(page);
    }

    private static Button windowButton(String label, String tip, Runnable pressed) {
        return button(label, pressed).setToolTipText(tip);
    }

    private static void toggleMaximize(View view) {
        if (view.geometry().join().maximized()) view.restore();
        else view.maximize();
    }

    /** A see-through, round window: only the page's own shape shows; drag it, double-click closes it. */
    static String transparent(App app) {
        Document page = ShowcaseUi.window(app, WindowOptions.html("").title("Transparent").size(320, 320)
                .decorations(false).transparent(true).alwaysOnTop(true).skipTaskbar(true).shadow(false));
        Div circle = Html.div(Html.text("A transparent window"), Html.br(), Html.text("(drag me; double-click closes)"));
        circle.setData("webview4j-drag-region", "").setPreferredSize(280, 280).style(CssProperty.BORDER_RADIUS, "50%")
                .setBackground("radial-gradient(circle at 30% 30%, #8ab4ff, #3157d5)").setForeground("white").setFont("600 16px system-ui")
                .setLayout(new StackLayout()).style(CssProperty.TEXT_ALIGN, "center").setCursor("move");
        View view = page.view();
        circle.on(EventType.DBLCLICK, e -> view.close());
        page.body().setBackground("transparent").setLayout(new StackLayout()).setPreferredSize(null, "100vh");
        page.body().add(circle);
        return opened(page);
    }

    /** Windows 11's material behind the window: the page is transparent where it should show. */
    static String backdrop(App app, String material) {
        Document page = ShowcaseUi.window(app, WindowOptions.html("").title(material + " backdrop").size(480, 320)
                .backdrop(WindowOptions.Backdrop.valueOf(material)).theme(Theme.DARK));
        page.body().setBackground("transparent").setForeground("white").setPadding("24px").setFont("15px system-ui");
        page.body().add(Html.h2(material).style(CssProperty.TEXT_TRANSFORM, "none").style(CssProperty.OPACITY, "1"),
                Html.p("The page's background is transparent: Windows 11 (22H2 or later) draws its material behind it. On older Windows, or"
                        + " elsewhere, nothing."));
        return opened(page);
    }

    static String owned(App app, View owner) {
        Document page = ShowcaseUi.window(app, WindowOptions.html("").title("Owned").size(420, 260).owner(owner));
        page.body().add(plain("An owned window", "It stays above the showcase, minimizes with it, and has no taskbar entry of its own."));
        return opened(page);
    }

    static String palette(App app, View owner) {
        Document page = ShowcaseUi.window(app, WindowOptions.html("").title("Palette").size(360, 220).focusable(false)
                .contentProtection(true).alwaysOnTop(true).owner(owner));
        page.body().add(plain("A palette", "It does not take the focus, and is left out of screen capture."));
        return opened(page);
    }

    static String embeddedIn(View host) {
        Document page = ShowcaseUi.embedded(host, 760, 600, 460, 220);
        page.body().add(plain("An embedded view", "A second webview inside the showcase's window: its own page, placed by Java."));
        return opened(page);
    }

    // ---- a browser of the app's own: its toolbar, the site in an embedded webview under it ----

    private static final class Browser {
        final Document host;
        final View site;
        final Input url = Html.input();
        final Span state = Html.span("").cssClass("hint");
        volatile boolean typing;

        Browser(Document host, View site, String start) {
            this.host = host;
            this.site = site;
            url.setValue(start).style(CssProperty.FLEX, "1");
            url.addFocusListener(e -> typing = e.type().equals("focus"));
            url.addKeyListener(e -> {
                if (!e.key().equals("Enter")) return;
                String to = url.getValue().strip();
                site.navigate(to.contains("://") ? to : "https://" + to);
            });
            Nav bar = Html.nav(Html.b("swaswa"), button("◀", site::back).setToolTipText("Back"), button("▶", site::forward).setToolTipText("Forward"),
                    button("⟳", site::reload).setToolTipText("Reload"), url, state);
            bar.setLayout(BoxLayout.row(6)).setPadding("0 10px").setPreferredSize(null, BAR + "px")
                    .style(CssProperty.BOX_SIZING, "border-box").setBorder("0").style(CssProperty.BORDER_BOTTOM, "1px solid " + Style.var("line"));
            host.body().add(bar);
        }

        void loaded(PageLoad load) {
            if (!typing) url.setValue(load.url());
            state.setText(load.state() == PageLoad.State.STARTED ? "loading…" : "");
        }
    }

    static String browser(App app, String url) {
        Document host = ShowcaseUi.window(app, WindowOptions.html("").title(url).size(1100, 780));
        Geometry g = host.view().geometry().join();
        View site = host.view().embed(WindowOptions.url(url), 0, BAR, g.width(), g.height() - BAR).join();
        browsers.put(site.id(), new Browser(host, site, url));
        layouts.put(host.view().id(), (w, h) -> site.place(0, BAR, w, Math.max(1, h - BAR)));
        return "a browser window of our own (" + host.view().id() + ") around " + url + " (" + site.id() + ")";
    }

    // ---- one window, several webviews ----

    static String multi(App app) {
        String[][] sites = {
                {"YouTube", "https://www.youtube.com/"},
                {"Wikipedia", "https://en.wikipedia.org/wiki/WebView"},
                {"example.com", "https://example.com/"},
                {"swaswa on GitHub", "https://github.com/joshuasalcedo-dev/swaswa"},
                {"Our own page", null},
        };
        Document host = ShowcaseUi.window(app, WindowOptions.html("").title("Several webviews, one window").size(1500, 940));
        Header header = Html.header(Html.b("Several webviews, one window").setFont("600 15px system-ui"),
                Html.span("Each pane is its own webview, placed by Java; resize the window and they follow.").cssClass("hint"));
        header.setLayout(BoxLayout.row(10)).setPadding("0 16px").setPreferredSize(null, BAR + "px").style(CssProperty.BOX_SIZING, "border-box")
                .setBackground(Style.var("card")).style(CssProperty.BORDER_BOTTOM, "1px solid " + Style.var("line"));
        Div captions = Html.div().setLayout(new AbsoluteLayout());   // each pane's caption, where Java puts it
        host.body().add(header, captions);
        Geometry g = host.view().geometry().join();
        List<View> views = new java.util.ArrayList<>();
        for (String[] site : sites) {
            View v;
            if (site[1] == null) {
                Document own = ShowcaseUi.embedded(host.view(), 0, BAR, 100, 100);
                own.body().add(plain("Our own page", "An HTML page of the app's own, in the same window as four external sites: five webviews, one window."));
                v = own.view();
            } else {
                v = host.view().embed(WindowOptions.url(site[1]), 0, BAR, 100, 100).join();
            }
            views.add(v);
        }
        int cols = 3, rows = 2;
        double gap = 8, caption = 22;
        BiConsumer<Double, Double> layout = (w, h) -> {
            double cellW = (w - gap * (cols + 1)) / cols, cellH = (h - BAR - gap * (rows + 1)) / rows;
            captions.removeAll();
            for (int i = 0; i < views.size(); i++) {
                double x = gap + (i % cols) * (cellW + gap), y = gap + (i / cols) * (cellH + gap);
                views.get(i).place(x, BAR + y + caption, cellW, Math.max(1, cellH - caption));
                Div cap = Html.div(sites[i][0] + (sites[i][1] == null ? "" : "  ·  " + sites[i][1]));
                cap.setFont("600 12px system-ui").setPadding("0 8px").style(CssProperty.LINE_HEIGHT, caption + "px")
                        .style(CssProperty.WHITE_SPACE, "nowrap").style(CssProperty.OVERFLOW, "hidden").style(CssProperty.TEXT_OVERFLOW, "ellipsis")
                        .setBackground(Style.lightDark("#dfe5ef", "#262930")).style(CssProperty.BORDER_RADIUS, "6px 6px 0 0")
                        .style(CssProperty.BOX_SIZING, "border-box");
                captions.add(cap, new AbsoluteLayout.Bounds(x, y, cellW, caption));
            }
        };
        layouts.put(host.view().id(), layout);
        layout.accept(g.width(), g.height());
        return "one window (" + host.view().id() + "), " + views.size() + " webviews";
    }

    // ---- graphics ----

    /**
     * A canvas Java draws on: each frame's pixels made in Java (a moving pattern), sent binary, drawn by the page; the
     * next sent once it is on the canvas. Stops when the window closes.
     */
    static String canvas(App app) {
        Document page = ShowcaseUi.window(app, WindowOptions.html("").title("Java draws this canvas").size(720, 520));
        Canvas screen = Html.canvas().setId("screen");
        screen.setPreferredSize(640, 400).style(CssProperty.IMAGE_RENDERING, "pixelated").style(CssProperty.BORDER_RADIUS, "8px");
        P rate = Html.p("starting…");
        page.body().setBackground("#111").setForeground("#ddd").setLayout(new StackLayout()).setPreferredSize(null, "100vh");
        page.body().add(Html.div(screen, rate));
        View window = page.view();
        int width = 320, height = 200;
        byte[] rgba = new byte[width * height * 4];
        // each frame painted for its time; one a display refresh, the next once the last is on the canvas
        Animation animation = window.animate("screen", t -> {
            for (int y = 0, i = 0; y < height; y++) {
                for (int x = 0; x < width; x++, i += 4) {
                    double v = Math.sin(x / 16.0 + t) + Math.sin(y / 11.0 - t * 1.3) + Math.sin((x + y) / 23.0 + t * 0.7);
                    rgba[i] = (byte) (128 + 127 * Math.sin(v * Math.PI));
                    rgba[i + 1] = (byte) (128 + 127 * Math.sin(v * Math.PI + 2));
                    rgba[i + 2] = (byte) (128 + 127 * Math.sin(v * Math.PI + 4));
                    rgba[i + 3] = (byte) 255;
                }
            }
            return Frame.rgba(width, height, rgba.clone());
        });
        // the display's own rate, counted by the page (only the page sees its refreshes)
        window.eval("window.__refreshes = 0; (function tick() { window.__refreshes++; requestAnimationFrame(tick); })()");
        Thread.ofVirtual().name("showcase-canvas-rate").start(() -> {
            long seen = 0;
            while (animation.running()) {
                try {
                    Thread.sleep(1000);
                    long refreshes = ((Number) window.eval("window.__refreshes").join()).longValue();
                    rate.setText(Math.round(animation.framesPerSecond()) + " frames a second, the display refreshing " + (refreshes - seen)
                            + " times a second · " + width + "×" + height + " RGBA pixels painted in Java each frame");
                    seen = refreshes;
                } catch (Exception closed) {
                    return;
                }
            }
        });
        return "a canvas Java draws on (" + window.id() + ")";
    }

    /** A sketch pad's picture, kept and drawn by Java. */
    private static final class Sketch {
        static final int WIDTH = 900, HEIGHT = 560;
        final View window;
        final byte[] rgba = new byte[WIDTH * HEIGHT * 4];
        double lastX = Double.NaN, lastY = Double.NaN;
        boolean down, sending, dirty;
        int r = 29, g = 31, b = 36;
        long points;

        Sketch(View window) {
            this.window = window;
            clear();
        }

        void clear() {
            for (int i = 0; i < rgba.length; i += 4) {
                rgba[i] = (byte) 250; rgba[i + 1] = (byte) 249; rgba[i + 2] = (byte) 245; rgba[i + 3] = (byte) 255;
            }
            lastX = Double.NaN;
        }

        /** A round dab of the pen at (x, y), its edge soft for one pixel. */
        void dab(double x, double y, double radius) {
            int x0 = (int) Math.floor(x - radius), x1 = (int) Math.ceil(x + radius);
            int y0 = (int) Math.floor(y - radius), y1 = (int) Math.ceil(y + radius);
            for (int py = Math.max(0, y0); py <= Math.min(HEIGHT - 1, y1); py++) {
                for (int px = Math.max(0, x0); px <= Math.min(WIDTH - 1, x1); px++) {
                    double d = Math.hypot(px - x, py - y);
                    if (d > radius) continue;
                    double a = Math.min(1, radius - d);
                    int i = (py * WIDTH + px) * 4;
                    rgba[i] = (byte) ((rgba[i] & 0xFF) * (1 - a) + r * a);
                    rgba[i + 1] = (byte) ((rgba[i + 1] & 0xFF) * (1 - a) + g * a);
                    rgba[i + 2] = (byte) ((rgba[i + 2] & 0xFF) * (1 - a) + b * a);
                }
            }
        }

        /** A line from the last point to (x, y): dabs half a radius apart, smooth however fast the pen moved. */
        void lineTo(double x, double y, double radius) {
            if (Double.isNaN(lastX)) {
                dab(x, y, radius);
            } else {
                double length = Math.hypot(x - lastX, y - lastY);
                int steps = Math.max(1, (int) Math.ceil(length / Math.max(0.5, radius / 2)));
                for (int k = 1; k <= steps; k++) dab(lastX + (x - lastX) * k / steps, lastY + (y - lastY) * k / steps, radius);
            }
            lastX = x;
            lastY = y;
            points++;
        }

        /** Sends the picture, unless one is on its way: then once that one is drawn. */
        synchronized void show() {
            if (sending) {
                dirty = true;
                return;
            }
            sending = true;
            dirty = false;
            window.drawFrame("pad", Frame.rgba(WIDTH, HEIGHT, rgba.clone())).whenComplete((drawn, error) -> {
                synchronized (this) {
                    sending = false;
                    if (error == null && dirty) show();
                }
            });
        }
    }

    /** A sketch pad: the canvas's pointer events come to Java, Java draws the lines into its own pixels and sends the picture. */
    static String sketch(App app) {
        Document page = ShowcaseUi.window(app, WindowOptions.html("").title("Sketch: Java draws what you draw").size(980, 680));
        Sketch sketch = new Sketch(page.view());
        Canvas pad = Html.canvas().setId("pad").setWidth(Sketch.WIDTH).setHeight(Sketch.HEIGHT);
        pad.setCursor("crosshair").style(CssProperty.TOUCH_ACTION, "none").style(CssProperty.BORDER_RADIUS, "8px")
                .style(CssProperty.BOX_SHADOW, "0 8px 30px #0006").style(CssProperty.DISPLAY, "block");
        Input size = Html.input().setType(Input.Type.RANGE).setMin(1).setMax(30).setValue("4");
        Span stats = Html.span("Draw with the mouse or a pen").setOpacity(.65);
        pad.on(EventType.POINTERDOWN, e -> {
            synchronized (sketch) {
                sketch.down = true;
                sketch.lastX = Double.NaN;
                sketch.lineTo(e.offsetX(), e.offsetY(), radius(size, e));
            }
            sketch.show();
        });
        pad.on(EventType.POINTERMOVE, e -> {
            synchronized (sketch) {
                if (!sketch.down) return;
                sketch.lineTo(e.offsetX(), e.offsetY(), radius(size, e));
            }
            sketch.show();
            stats.setText(sketch.points + " points drawn by Java");
        });
        for (EventType end : new EventType[] {EventType.POINTERUP, EventType.POINTERLEAVE, EventType.POINTERCANCEL}) {
            pad.on(end, e -> {
                synchronized (sketch) {
                    sketch.down = false;
                }
            });
        }
        Div colors = Html.div().setLayout(BoxLayout.row(8));
        int[][] palette = {{29, 31, 36}, {210, 60, 60}, {47, 111, 214}, {47, 158, 90}, {245, 179, 1}};
        for (int[] c : palette) {
            Button swatch = Html.button();
            swatch.setPreferredSize(24, 24).style(CssProperty.BORDER_RADIUS, "50%").setPadding("0")
                    .setBackground("rgb(" + c[0] + "," + c[1] + "," + c[2] + ")").setToolTipText("rgb(" + c[0] + "," + c[1] + "," + c[2] + ")");
            swatch.addActionListener(e -> {
                synchronized (sketch) {
                    sketch.r = c[0];
                    sketch.g = c[1];
                    sketch.b = c[2];
                }
                for (var other : colors.getComponents()) if (other instanceof Button o) o.setBorder(null);
                swatch.setBorder("2px solid white");
            });
            colors.add(swatch);
        }
        Button clear = button("Clear", () -> {
            synchronized (sketch) {
                sketch.clear();
            }
            sketch.show();
        });
        Div bar = Html.div(colors, Html.label(Html.text("Size "), size), clear, stats).setLayout(BoxLayout.row(8)).setMargin("0 0 8px");
        page.body().setBackground("#2a2b30").setForeground("#ddd").setLayout(new StackLayout()).setPreferredSize(null, "100vh");
        page.body().add(Html.div(bar, pad));
        sketch.show();
        return "a sketch pad (" + page.view().id() + "): draw on it; Java draws each line";
    }

    /** The pen's size, by its pressure when it is a pen. */
    private static double radius(Input size, io.joshuasalcedo.swaswa.component.Event e) {
        double base = Double.parseDouble(size.getValue() == null ? "4" : size.getValue()) / 2;
        return Math.max(0.5, e.pointerType().equals("pen") && e.pressure() > 0 ? base * e.pressure() * 2 : base);
    }

    // ---- egui: an immediate-mode UI drawn from Java (its own toolkit, not HTML) ----

    private static int clicks;
    private static boolean setting = true;
    private static double volume = 40;
    private static String name = "";

    static String egui(App app) {
        ImmediateWindow window = Egui.on(app).open("egui, drawn from Java", 420, 380, ui -> {
            ui.heading("egui, from Java");
            ui.label("Each frame, Java draws this anew from its own state; egui draws it with the GPU.");
            ui.separator();
            ui.horizontal(row -> {
                if (row.button("Click me")) clicks++;
                row.label("clicked " + clicks + (clicks == 1 ? " time" : " times"));
            });
            setting = ui.checkbox("A setting", setting);
            volume = ui.slider("Volume", volume, 0, 100);
            ui.label("Volume is " + Math.round(volume));
            ui.space(6);
            ui.label("Your name:");
            name = ui.textField("name", name);
            if (!name.isBlank()) ui.label("Hello, " + name + "!");
        });
        return "an egui window (" + window.id() + "): click, slide and type in it";
    }

    static String eguiFrameless(App app) {
        Egui egui = Egui.on(app);
        egui.open(Immediate.Options.of("egui: its own title bar", 420, 300).frameless(), ui -> {
            ui.titleBar("egui: its own title bar");
            ui.label("Drag the bar to move this window, double-click it to maximize;");
            ui.label("its buttons minimize, maximize and close. Its edges size it.");
        });
        egui.open(Immediate.Options.of("egui: a bar of its own design", 460, 300).frameless(), ui -> {
            ui.dragRegion(40, bar -> {
                bar.heading("◆ Brand");
                bar.button("File");
                bar.button("View");
                bar.space(20);
                if (bar.button("—")) bar.window().minimize();
                if (bar.button("☐")) bar.window().toggleMaximize();
                if (bar.button("✕")) bar.window().close();
            });
            ui.separator();
            ui.label("A bar of the app's own: drag its empty parts to move the window;");
            ui.label("its buttons are buttons, its own minimize, maximize and close among them.");
        });
        return "two egui windows without the system's frame";
    }

    /**
     * egui inside a page: the page's own header and buttons (HTML components) around an {@link ImmediatePanel}, a fast
     * log drawn by egui where the panel's element is; resize the window, the log follows its element.
     */
    static String eguiInPage(App app) {
        Document page = ShowcaseUi.window(app, WindowOptions.html("").title("egui inside a page").size(900, 640));
        List<String> lines = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        java.util.concurrent.atomic.AtomicBoolean paused = new java.util.concurrent.atomic.AtomicBoolean();
        io.joshuasalcedo.swaswa.html.ImmediatePanel log = new io.joshuasalcedo.swaswa.html.ImmediatePanel(ui ->
                ui.rows("log", lines.size(), 0, true, (rows, from, to) -> {
                    for (int i = from; i < to; i++) rows.monospace(lines.get(i));
                }));
        log.style(CssProperty.FLEX, "1").setMinimumSize(null, "0").setBorder("1px solid " + Style.var("line"));
        Span count = Html.span("0 lines").cssClass("hint");
        Button pause = Html.button("Pause");
        pause.addActionListener(e -> {
            boolean now = !paused.get();
            paused.set(now);
            pause.setText(now ? "Go on" : "Pause");
        });
        Header head = Html.header(Html.b("egui inside a page").setFont("600 15px system-ui"), pause,
                button("Clear", () -> {
                    lines.clear();
                    log.repaint();
                }), count);
        head.setLayout(BoxLayout.row(10)).setPadding("10px 14px").style(CssProperty.BORDER_BOTTOM, "1px solid " + Style.var("line"));
        P about = Html.p("The header, its buttons and this sentence are HTML components; the log under them is egui, a native panel"
                + " that follows its element as the page lays it out.").cssClass("hint").setMargin("8px 14px");
        Div body = Html.div(head, about, log).setLayout(BoxLayout.column(0));
        body.setPreferredSize(null, "100vh").style(CssProperty.BOX_SIZING, "border-box").setPadding("0 0 12px");
        log.setMargin("0 14px");
        page.body().add(body);
        Thread.ofVirtual().name("showcase-egui-in-page").start(() -> {
            long n = 0;
            while (app.views().join().stream().anyMatch(v -> v.id().equals(page.view().id()))) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    return;
                }
                if (paused.get()) continue;
                for (int i = 0; i < 40; i++, n++) lines.add(String.format("%s #%07d a line from Java", java.time.LocalTime.now().withNano(0), n));
                log.repaint();
                if (n % 400 == 0) count.setText(String.format("%,d lines", lines.size()));
            }
        });
        return "egui inside a page (" + page.view().id() + "): HTML around a native egui panel";
    }

    static String eguiLog(App app) {
        List<String> lines = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        java.util.concurrent.atomic.AtomicBoolean paused = new java.util.concurrent.atomic.AtomicBoolean();
        java.util.concurrent.atomic.AtomicBoolean follow = new java.util.concurrent.atomic.AtomicBoolean(true);
        double[] rate = {2000};
        long[] counted = {0, System.nanoTime()};
        double[] shownRate = {0};
        String[] levels = {"INFO ", "DEBUG", "WARN ", "INFO ", "TRACE", "ERROR"};
        String[] words = {"request served", "cache refreshed", "user signed in", "file indexed", "job queued", "socket opened",
                "retrying upload", "page rendered", "query took 3 ms", "token renewed"};
        ImmediateWindow window = Egui.on(app).open("A fast log, egui from Java", 760, 520, ui -> {
            ui.horizontal(bar -> {
                bar.heading("Log");
                paused.set(bar.checkbox("Pause", paused.get()));
                follow.set(bar.checkbox("Follow the end", follow.get()));
                if (bar.button("Clear")) lines.clear();
            });
            rate[0] = ui.slider("lines a second", rate[0], 100, 20_000);
            long now = System.nanoTime();
            if (now - counted[1] > 500_000_000L) {
                int total = lines.size();
                shownRate[0] = (total - counted[0]) * 1e9 / (now - counted[1]);
                counted[0] = total;
                counted[1] = now;
            }
            ui.label(String.format("%,d lines · %,.0f a second · only those in view are drawn", lines.size(), Math.max(0, shownRate[0])));
            ui.separator();
            ui.rows("log", lines.size(), 0, follow.get(), (rows, from, to) -> {
                for (int i = from; i < to; i++) rows.monospace(lines.get(i));
            });
        });
        Thread.ofVirtual().name("showcase-egui-log").start(() -> {
            long n = 0;
            java.util.Random random = new java.util.Random(7);
            while (!window.whenClosed().isDone()) {
                try {
                    Thread.sleep(16);
                } catch (InterruptedException e) {
                    return;
                }
                if (paused.get()) continue;
                int batch = (int) Math.max(1, rate[0] * 0.016);
                String time = java.time.LocalTime.now().withNano(0).toString();
                for (int i = 0; i < batch; i++, n++) {
                    lines.add(String.format("%s.%03d %s #%07d %s", time, (n % 1000), levels[random.nextInt(levels.length)], n,
                            words[random.nextInt(words.length)]));
                }
                window.repaint();
            }
        });
        return "a fast log in egui (" + window.id() + "): scroll up to stop following, back to the end to follow again";
    }

    // ---- injecting into a site that is not ours: scripts and CSS are the point here ----

    private static volatile View injected;

    /** Given to the Wikipedia window when it opens: runs on every page before the page's own scripts. */
    private static final String INIT_SCRIPT = """
            addEventListener('DOMContentLoaded', () => {
              const badge = document.createElement('div');
              badge.textContent = 'swaswa initialization script';
              Object.assign(badge.style, { position: 'fixed', right: '12px', bottom: '12px', zIndex: 99999, background: '#111d',
                color: '#9fe3b4', padding: '6px 10px', borderRadius: '8px', font: '12px ui-monospace, monospace' });
              document.body.append(badge);
            });""";

    static synchronized View injected(App app, boolean fresh) {
        View w = injected;
        if (!fresh && w != null && app.views().join().stream().anyMatch(v -> v.id().equals(w.id()))) return w;
        if (w != null) w.close();
        injected = app.open(WindowOptions.url("https://en.wikipedia.org/wiki/Java_(programming_language)")
                .title("Wikipedia, injected by swaswa").size(1100, 820).initializationScript(INIT_SCRIPT)).join();
        return injected;
    }

    static String injectCss(App app, String which) {
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
        Object length = injected(app, false).eval(script).join();
        return css.isEmpty() ? "the injected CSS taken out" : "injected " + length + " characters of CSS: " + which.toLowerCase();
    }

    static String injectScript(App app) {
        Object found = injected(app, false).eval("""
                (() => {
                  let b = document.getElementById('swaswa-banner');
                  if (!b) {
                    b = document.createElement('div'); b.id = 'swaswa-banner';
                    Object.assign(b.style, { position: 'fixed', left: '50%', top: '14px', transform: 'translateX(-50%)', zIndex: 99999,
                      background: '#3157d5', color: 'white', padding: '10px 18px', borderRadius: '999px', font: '600 14px system-ui',
                      boxShadow: '0 6px 24px #0004' });
                    document.body.append(b);
                  }
                  const links = [...document.querySelectorAll('#mw-content-text a[href]')]
                    .filter((a) => new URL(a.href).pathname.startsWith('/wiki/') && !a.closest('.reference')).length;
                  const title = document.querySelector('#firstHeading')?.textContent ?? document.title;
                  b.textContent = 'Injected by swaswa: ' + links + ' links in "' + title + '"';
                  return { title, links, url: location.href };
                })()""").join();
        return "the page answered Java: " + found;
    }

    /** Listens to one kind of the Wikipedia page's doings, or stops; for network calls, those it made before, from its own record. */
    static String listen(App app, Capture kind, boolean on, BiConsumer<String, String> log) {
        View wiki = injected(app, false);
        wiki.capture(kind, on).join();
        if (on && kind == Capture.NETWORK) {
            Object before = wiki.eval("""
                    performance.getEntriesByType('resource').map((r) => ({ kind: r.initiatorType, url: r.name,
                      ms: Math.round(r.duration), bytes: r.transferSize }))""").join();
            if (before instanceof List<?> list) {
                for (Object entry : list) {
                    if (entry instanceof Map<?, ?> r) {
                        log.accept("Loaded before", wiki.id() + "  · " + r.get("kind") + " " + r.get("url") + "  " + r.get("ms") + " ms, " + r.get("bytes") + " bytes");
                    }
                }
                return "listening to network calls in the Wikipedia window; " + list.size() + " were made before (listed)."
                        + " New ones come as the page makes them: go to another article, search, or hover a link";
            }
        }
        return (on ? "listening to " : "no longer listening to ") + kind.name().toLowerCase() + " in the Wikipedia window";
    }
}
