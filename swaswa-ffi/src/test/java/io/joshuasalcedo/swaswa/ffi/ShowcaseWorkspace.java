package io.joshuasalcedo.swaswa.ffi;

import static io.joshuasalcedo.swaswa.ffi.ShowcaseUi.button;

import io.joshuasalcedo.swaswa.component.BoxLayout;
import io.joshuasalcedo.swaswa.component.EventType;
import io.joshuasalcedo.swaswa.component.FlowLayout;
import io.joshuasalcedo.swaswa.css.CssProperty;
import io.joshuasalcedo.swaswa.css.Style;
import io.joshuasalcedo.swaswa.css.StyleSheet;
import io.joshuasalcedo.swaswa.html.Button;
import io.joshuasalcedo.swaswa.html.Div;
import io.joshuasalcedo.swaswa.html.Document;
import io.joshuasalcedo.swaswa.html.Html;
import io.joshuasalcedo.swaswa.html.HtmlElement;
import io.joshuasalcedo.swaswa.html.Span;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The workspace: windows dragged onto it are docked in it, MDI children move about inside it. Done with the flags:
 * the workspace opens {@code dropTarget(true)}, what can go in opens {@code draggable(true)} (an embedded one is then an
 * MDI child); the app's own docking ({@link App#docking()}) does the rest. Its pages, and its children's, are components.
 */
final class ShowcaseWorkspace {

    private ShowcaseWorkspace() {}

    private static volatile Document workspace;
    private static volatile Div inside;
    /** What has been in the workspace: in it, or popped out (and able to go back). */
    private record Item(String what, boolean child, boolean out, double[] at) {}
    private static final Map<String, Item> items = new ConcurrentHashMap<>();
    /** The MDI children's own bars, to show them as in or out. */
    private static final Map<String, Child> bars = new ConcurrentHashMap<>();
    private static int children;
    private static boolean dockingSet;

    static synchronized String open(App app) {
        Document w = workspace;
        if (w != null && app.views().join().stream().anyMatch(v -> v.id().equals(w.view().id()))) {
            w.view().focus();
            return "the workspace is open: drag a window onto it";
        }
        items.clear();
        Document page = ShowcaseUi.window(app, WindowOptions.html("").title("Workspace: drag windows in").size(1100, 720).position(120, 80)
                .dropTarget(true));
        page.addStyleSheet(new StyleSheet()
                .rule("body", s -> s.set(CssProperty.HEIGHT, "100vh").set(CssProperty.BACKGROUND, Style.lightDark("#e4e8ef", "#1b1d22"))
                        .set(CssProperty.BACKGROUND_IMAGE, "radial-gradient(" + Style.lightDark("#cfd6e2", "#2a2e36") + " 1px, transparent 1px)")
                        .set(CssProperty.BACKGROUND_SIZE, "18px 18px"))
                .rule("footer", s -> s.set(CssProperty.POSITION, "fixed").set(CssProperty.LEFT, "0").set(CssProperty.RIGHT, "0").set(CssProperty.BOTTOM, "0")
                        .set(CssProperty.PADDING, "8px 12px").set(CssProperty.BACKGROUND, Style.var("card")).set(CssProperty.BORDER_TOP, "1px solid " + Style.var("line")))
                .rule(".sep", s -> s.set(CssProperty.WIDTH, "1px").set(CssProperty.ALIGN_SELF, "stretch").set(CssProperty.BACKGROUND, Style.var("line")))
                .rule(".item", s -> s.set(CssProperty.PADDING, "2px 4px 2px 9px").set(CssProperty.BORDER_RADIUS, "6px")
                        .set(CssProperty.BACKGROUND, Style.lightDark("#eef2f9", "#2c3038")))
                .rule(".item.out", s -> s.set(CssProperty.OPACITY, ".7").set(CssProperty.FONT_STYLE, "italic"))
                .rule(".tip", s -> s.set(CssProperty.POSITION, "fixed").set(CssProperty.TOP, "40%").set(CssProperty.LEFT, "0").set(CssProperty.RIGHT, "0")
                        .set(CssProperty.TEXT_ALIGN, "center").set(CssProperty.OPACITY, ".45").set(CssProperty.FONT_SIZE, "15px")
                        .set(CssProperty.POINTER_EVENTS, "none")));
        Div list = Html.div().setLayout(new FlowLayout(FlowLayout.Align.TRAILING, 6, 6)).style(CssProperty.MARGIN_LEFT, "auto");
        inside = list;
        Div bar = Html.div(Html.b("Workspace"), Html.span("Open:"),
                button("+ Wikipedia", () -> site(app, "wikipedia")), button("+ example.com", () -> site(app, "example")),
                button("+ GitHub", () -> site(app, "github")), button("+ Our own page", () -> site(app, "own")),
                Html.span().cssClass("sep"), Html.span("Inside:"),
                button("+ Note", () -> child(app, "note")), button("+ Clock", () -> child(app, "clock")), button("+ Counter", () -> child(app, "counter")),
                Html.span().cssClass("sep"), Html.span("Arrange:"),
                button("⧉ Cascade", () -> arrange(app, MdiLayout.CASCADE)), button("▦ Grid", () -> arrange(app, MdiLayout.GRID)),
                button("☰ Rows", () -> arrange(app, MdiLayout.ROWS)), button("▥ Columns", () -> arrange(app, MdiLayout.COLUMNS)),
                list).setLayout(new FlowLayout(FlowLayout.Align.LEADING, 6, 6));
        page.body().add(Html.div(Html.text("Drag a window onto the workspace to dock it here · hold Ctrl to keep it a window"), Html.br(),
                Html.text("or add MDI children and move them about inside · right-click a child's bar for more")).cssClass("tip"),
                Html.footer(bar).setId("bar"));
        workspace = page;
        if (!dockingSet) {
            dockingSet = true;
            app.docking()
                    .grow(false)   // a workspace keeps its size: a panel takes its room inside it
                    .onDock(drop -> {
                        drop.host().adopt(drop.dragged(), drop.x(), drop.y(), drop.width(), drop.height()).join();
                        Item was = items.get(drop.dragged().id());
                        if (was != null && was.child()) drop.dragged().setDraggable(true).join();   // an MDI child again
                        String what = was != null ? was.what() : "docked · " + drop.zone().name().toLowerCase();
                        items.put(drop.dragged().id(), new Item(what, was != null && was.child(), false,
                                new double[] {drop.x(), drop.y(), drop.width(), drop.height()}));
                        showOut(drop.dragged().id(), false);
                        list();
                    });
            app.subscribe(Closed.class, e -> {
                bars.remove(e.view());
                if (items.remove(e.view()) != null) list();
            });
        }
        return "the workspace (" + page.view().id() + "): open windows with its buttons and drag them onto it";
    }

    /** What is (or was) in the workspace, each with its buttons. */
    private static void list() {
        Div list = inside;
        if (list == null) return;
        list.removeAll();
        App app = workspace.view().app();
        items.forEach((id, item) -> {
            Span entry = Html.span(item.what() + (item.out() ? " (out)" : "") + " ").cssClass("item");
            if (item.out()) entry.cssClass("out");
            entry.add(item.out() ? button("↩ back in", () -> back(app, id)) : button("↗ pop out", () -> out(app, id)),
                    button("close", () -> app.view(id).close()));
            list.add(entry);
        });
    }

    /** A window to drag into the workspace: a site, or a page of the app's own. */
    private static void site(App app, String what) {
        WindowOptions options = switch (what) {
            case "wikipedia" -> WindowOptions.url("https://en.wikipedia.org/wiki/Docking").title("Wikipedia");
            case "example" -> WindowOptions.url("https://example.com/").title("example.com");
            case "github" -> WindowOptions.url("https://github.com/joshuasalcedo-dev/swaswa").title("swaswa on GitHub");
            default -> WindowOptions.html("").title("Our own page");
        };
        options.size(460, 360).position(1250, 120 + 40 * (int) (Math.random() * 6)).draggable(true);
        if (what.equals("own")) {
            Document page = ShowcaseUi.window(app, options);
            page.body().add(ShowcaseUi.plain("Our own page", "A page of the app's own. Drag this window by its title bar onto the workspace: "
                    + "the place it would take lights up; let go, and it is a panel there (Ctrl: keep it a window)."));
        } else {
            app.open(options).join();
        }
    }

    /** An MDI child's own bar: its title, pop out or back in, close; and the same in its menu. */
    private record Child(Button out, Button back, Button menuOut, Button menuBack, Button menuFront) {}

    /** An MDI child in the workspace: its own title bar moves it about inside, its edges size it, a click raises it. */
    private static void child(App app, String what) {
        Document host = workspace;
        if (host == null) return;
        int n = ++children;
        String title = switch (what) { case "clock" -> "Clock"; case "counter" -> "Counter"; default -> "Note"; } + " " + n;
        double[] at = {60 + 30 * (n % 8), 90 + 26 * (n % 8), 320, 220};
        View view = host.view().embed(WindowOptions.html("").origins("null").draggable(true), at[0], at[1], at[2], at[3]).join();   // an MDI child
        Document page = io.joshuasalcedo.swaswa.html.Document.of(view).addStyleSheet(ShowcaseUi.LOOK);
        page.addStyleSheet(new StyleSheet()
                .rule("html, body", s -> s.set(CssProperty.HEIGHT, "100%"))
                .rule("body", s -> s.set(CssProperty.DISPLAY, "flex").set(CssProperty.FLEX_DIRECTION, "column").set(CssProperty.BACKGROUND, Style.var("card"))
                        .set(CssProperty.BORDER, "1px solid " + Style.lightDark("#b9c2d0", "#444a55")).set(CssProperty.BOX_SIZING, "border-box"))
                .rule(".bar", s -> s.set(CssProperty.HEIGHT, "28px").set(CssProperty.PADDING, "0 4px 0 10px").set(CssProperty.COLOR, "#fff")
                        .set(CssProperty.BACKGROUND, "#7d8697").set(CssProperty.USER_SELECT, "none"))
                .rule("html[data-swaswa-active] .bar", s -> s.set(CssProperty.BACKGROUND, "#3157d5"))
                .rule(".bar button", s -> s.set(CssProperty.BORDER, "0").set(CssProperty.BACKGROUND, "transparent").set(CssProperty.COLOR, "#fff")
                        .set(CssProperty.WIDTH, "24px").set(CssProperty.HEIGHT, "22px").set(CssProperty.PADDING, "0"))
                .rule(".bar button:hover", s -> s.set(CssProperty.BACKGROUND, "#fff3"))
                .rule(".menu", s -> s.set(CssProperty.POSITION, "fixed").set(CssProperty.MIN_WIDTH, "190px").set(CssProperty.PADDING, "4px")
                        .set(CssProperty.BORDER_RADIUS, "8px").set(CssProperty.Z_INDEX, "9").set(CssProperty.BACKGROUND, Style.var("card"))
                        .set(CssProperty.BORDER, "1px solid " + Style.var("line")).set(CssProperty.BOX_SHADOW, "0 6px 20px #0004"))
                .rule(".menu button", s -> s.set(CssProperty.DISPLAY, "block").set(CssProperty.WIDTH, "100%").set(CssProperty.TEXT_ALIGN, "left")
                        .set(CssProperty.BORDER, "0").set(CssProperty.BACKGROUND, "none")));

        Button out = button("↗", () -> out(app, view.id())).setToolTipText("Pop out into a window of its own");
        Button back = button("↩", () -> back(app, view.id())).setToolTipText("Back into the workspace");
        back.setVisible(false);
        Div bar = Html.div(Html.span(title).style(CssProperty.FLEX, "1").setFont("600 13px system-ui"), out, back,
                button("✕", view::close).setToolTipText("Close")).cssClass("bar").setLayout(BoxLayout.row(4));
        bar.setData("webview4j-drag-region", "");

        HtmlElement<?> body = switch (what) {
            case "clock" -> {
                Div clock = Html.div(java.time.LocalTime.now().withNano(0).toString()).setFont("600 34px ui-monospace, monospace")
                        .style(CssProperty.TEXT_ALIGN, "center").setMargin("18px 0 0");
                Thread.ofVirtual().name("showcase-clock").start(() -> {   // Java keeps the time on it
                    while (bars.containsKey(view.id()) || items.containsKey(view.id())) {
                        clock.setText(java.time.LocalTime.now().withNano(0).toString());
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            return;
                        }
                    }
                });
                yield clock;
            }
            case "counter" -> {
                int[] count = {0};
                Span number = Html.span("0").setFont("600 30px system-ui");
                Button plus = button("+1", () -> number.setText(String.valueOf(++count[0])));
                yield ShowcaseUi.column(8, plus, number).style(CssProperty.ALIGN_ITEMS, "center").setMargin("14px 0 0");
            }
            default -> Html.textarea().setPlaceholder("Type a note. Drag the bar above to move this; its edges size it; right-click the bar for more.")
                    .style(CssProperty.WIDTH, "100%").style(CssProperty.HEIGHT, "100%").style(CssProperty.BOX_SIZING, "border-box")
                    .setBorder("0").setPadding("10px").setFont("14px system-ui").style(CssProperty.RESIZE, "none").style(CssProperty.OUTLINE, "none");
        };

        // a menu of its own on a right-click of its bar
        Button menuOut = button("↗  Pop out into a window", () -> out(app, view.id()));
        Button menuBack = button("↩  Back into the workspace", () -> back(app, view.id()));
        Button menuFront = button("▣  Bring to front", view::toFront);
        menuBack.setVisible(false);
        Div menu = Html.div(menuOut, menuBack, menuFront, button("✕  Close", view::close)).cssClass("menu");
        menu.setVisible(false);
        bar.on(EventType.CONTEXTMENU, e -> {
            menu.style(CssProperty.LEFT, Math.min(e.x(), at[2] - 200) + "px").style(CssProperty.TOP, e.y() + "px");
            menu.setVisible(true);
        });
        page.body().on(EventType.POINTERDOWN, e -> {
            if (e.button() == 0) menu.setVisible(false);
        });
        for (var item : menu.getComponents()) {
            if (item instanceof Button b) b.addActionListener(e -> menu.setVisible(false));
        }
        page.body().add(bar, Html.main(body).style(CssProperty.FLEX, "1").setMinimumSize(null, "0"), menu);

        bars.put(view.id(), new Child(out, back, menuOut, menuBack, menuFront));
        view.toFront();
        items.put(view.id(), new Item("MDI · " + title, true, false, at));
        list();
    }

    /** An MDI child's bar shows whether it is out of the workspace: back in, or pop out. */
    private static void showOut(String id, boolean out) {
        Child c = bars.get(id);
        if (c == null) return;
        c.out().setVisible(!out);
        c.back().setVisible(out);
        c.menuOut().setVisible(!out);
        c.menuBack().setVisible(out);
        c.menuFront().setVisible(!out);
    }

    /** Out of the workspace into a window of its own, its page as it was. */
    private static void out(App app, String id) {
        View v = app.view(id);
        Item item = items.get(id);
        if (item == null || item.out()) return;
        v.setFloating(false).exceptionally(already -> null).join();
        v.detach(item.what().replace("MDI · ", ""), 1250, 160, 480, 380).join();
        items.put(id, new Item(item.what(), item.child(), true, item.at()));
        showOut(id, true);
        list();
    }

    /** Back into the workspace where it was. */
    private static void back(App app, String id) {
        Document host = workspace;
        Item item = items.get(id);
        if (host == null || item == null || !item.out()) return;
        View v = app.view(id);
        double[] at = item.at();
        host.view().adopt(v, at[0], at[1], at[2], at[3]).join();
        if (item.child()) {
            v.setDraggable(true).join();   // an MDI child again
            v.toFront();
        }
        items.put(id, new Item(item.what(), item.child(), false, at));
        showOut(id, false);
        list();
    }

    /** The MDI children in the workspace laid out at once: cascade, grid, rows or columns (above its bar). */
    private static void arrange(App app, MdiLayout layout) {
        Document host = workspace;
        if (host == null) return;
        List<String> ids = items.entrySet().stream().filter(e -> e.getValue().child() && !e.getValue().out()).map(Map.Entry::getKey).sorted().toList();
        if (ids.isEmpty()) return;
        // how much room there is above the bar: only the page knows
        Object room = host.view().eval("[innerWidth, innerHeight - document.getElementById('bar').offsetHeight]").join();
        List<?> wh = (List<?>) room;
        double w = ((Number) wh.get(0)).doubleValue(), h = ((Number) wh.get(1)).doubleValue();
        host.view().arrange(layout, ids.stream().map(app::view).toList(), 0, 0, w, h).join();
        List<double[]> places = layout.places(ids.size(), 0, 0, w, h);
        for (int i = 0; i < ids.size(); i++) {
            Item item = items.get(ids.get(i));
            if (item != null) items.put(ids.get(i), new Item(item.what(), true, false, places.get(i)));
        }
    }
}
