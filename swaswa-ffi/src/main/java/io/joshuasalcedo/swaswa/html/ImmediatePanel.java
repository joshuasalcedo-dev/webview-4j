package io.joshuasalcedo.swaswa.html;

import io.joshuasalcedo.swaswa.ffi.App;
import io.joshuasalcedo.swaswa.ffi.Egui;
import io.joshuasalcedo.swaswa.ffi.Immediate;
import io.joshuasalcedo.swaswa.ffi.ImmediateWindow;
import io.joshuasalcedo.swaswa.ffi.Ui;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * An immediate-mode UI as a component of a page: put it in the page like any element, size it with CSS or a layout,
 * and {@code frame} draws in it each frame ({@link Egui}'s, unless another {@link Immediate} is given). Where it is in
 * the page, the UI is: when it moves, is sized, scrolls or is hidden, the UI follows; taken out of the page, it closes.
 * <pre>{@code
 * ImmediatePanel log = new ImmediatePanel(ui -> ui.rows("log", lines.size(), 0, true, (rows, from, to) -> { ... }));
 * log.setPreferredSize("100%", "300px");
 * page.getMain().add(log);
 * }</pre>
 * It is drawn natively, over the page: the page cannot draw over it (its own menus and tool tips go under it).
 * Windows only, for now.
 */
public final class ImmediatePanel extends HtmlElement<ImmediatePanel> {

    private final Consumer<Ui> frame;
    private final Function<App, Immediate> drawer;
    private volatile ImmediateWindow panel;

    /** Drawn by egui. */
    public ImmediatePanel(Consumer<Ui> frame) {
        this(frame, Egui::on);
    }

    /** Drawn by the {@link Immediate} {@code drawer} gives for the app. */
    public ImmediatePanel(Consumer<Ui> frame, Function<App, Immediate> drawer) {
        super("div", false);
        this.frame = Objects.requireNonNull(frame, "frame");
        this.drawer = Objects.requireNonNull(drawer, "drawer");
        setData("swaswa-immediate", "");
    }

    /** Its UI, once it is in a shown page; null before. */
    public ImmediateWindow getPanel() {
        return panel;
    }

    /** A frame soon: for a change of the app's own the UI should show (a value a worker set). */
    public ImmediatePanel repaint() {
        ImmediateWindow p = panel;
        if (p != null) p.repaint();
        return this;
    }

    @Override
    Map<String, Object> tree(Document document) {
        Map<String, Object> t = super.tree(document);
        t.put("track", true);   // the page tells where it is
        return t;
    }

    @Override
    void fire(String type, Map<String, Object> data, Node dragged) {
        if (!type.equals("bounds")) {
            super.fire(type, data, dragged);
            return;
        }
        double x = number(data, "bx"), y = number(data, "by"), w = number(data, "bw"), h = number(data, "bh");
        Document d = document;
        if (d == null) return;
        synchronized (this) {
            ImmediateWindow p = panel;
            if (p == null || p.whenClosed().isDone()) {
                if (w < 1 || h < 1) return;   // not shown yet: made once it is
                panel = drawer.apply(d.view().app()).embed(d.view(), x, y, w, h, frame);
            } else {
                p.place(x, y, w, h);
            }
        }
    }

    @Override
    void leave() {
        ImmediateWindow p;
        synchronized (this) {
            p = panel;
            panel = null;
        }
        if (p != null) p.close();
        super.leave();
    }

    private static double number(Map<String, Object> data, String name) {
        return data.get(name) instanceof Number n ? n.doubleValue() : 0;
    }
}
