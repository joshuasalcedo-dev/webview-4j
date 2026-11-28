package io.joshuasalcedo.swaswa.ffi;

import io.joshuasalcedo.swaswa.internal.Json;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Windows docked into windows by a person, as IDEs and browsers do: while a window is dragged over another, where it
 * would go shows in that one, and let go there it goes in ({@link View#adopt}); with a key held (Ctrl unless set), it
 * stays a window of its own. How it shows is the {@link Style}:
 * <ul>
 *   <li>{@link Style#TARGET_AREA} (the default, as IntelliJ and VS Code): the area it would take lights up: the half by
 *       the edge the pointer is near, or all of it in the middle;</li>
 *   <li>{@link Style#GUIDES} (as Visual Studio): a compass of guides in the middle; it docks only let go on one;</li>
 *   <li>{@link Style#TAB_STRIP} (as a browser's tabs): only let go over the window's strip (a band at its top) it docks,
 *       as a tab.</li>
 * </ul>
 * Each is drawn above all the window has (its embedded views too), in a transparent view of its own, and gone when the
 * drag is. Which windows may be docked and which take them, the zones, the key, the colour and what docking does
 * ({@link #onDock}) are the app's to set.
 *
 * <pre>{@code
 * Docking docking = Docking.on(app).style(Docking.Style.GUIDES).keepFloatingWith(Docking.Key.SHIFT).start();
 * }</pre>
 */
public final class Docking implements AutoCloseable {

    public enum Style { TARGET_AREA, GUIDES, TAB_STRIP }

    /** Where in the host a view goes: all of it, or the half by one edge. */
    public enum Zone { CENTER, LEFT, RIGHT, TOP, BOTTOM }

    /** The key that, held as the window is let go, keeps it floating. */
    public enum Key { CTRL, SHIFT, ALT, NONE }

    /** A view let go over a zone of a host: where it goes there, in the host's logical pixels. */
    public record Drop(View dragged, View host, Zone zone, double x, double y, double width, double height) {}

    private final App app;
    private Style style = Style.TARGET_AREA;
    private Set<Zone> zones = EnumSet.allOf(Zone.class);
    private Key key = Key.CTRL;
    private String color = "rgba(49, 87, 213, 0.28)";
    private String outline = "#3157d5";
    private double edge = 0.25;
    private double strip = 40;
    private Predicate<View> dockable = v -> true;
    private Predicate<View> host = v -> true;
    private boolean hints = true;
    private boolean hostHighlight = true;
    private boolean settle = true;
    private byte[] sound;
    private int[] frame = {0x31, 0x57, 0xd5};
    private volatile View framed;
    private java.util.function.BiFunction<Zone, Boolean, String> hintText = this::defaultHint;
    private boolean grow = true;
    private double share = 0.35;
    private double smallest = 160;
    private Consumer<Drop> onDock = this::layOut;
    /** Each host's docked panels, by side, in the order they came: what lays it out. */
    private final Map<String, java.util.List<Panel>> panels = new ConcurrentHashMap<>();
    private Subscription leaving;

    /** A view docked at a side of a host, and its size across that side (logical pixels). */
    record Panel(View view, Zone side, double size) {}
    private Subscription subscription;
    private final Map<String, View> overlays = new ConcurrentHashMap<>();
    private final Map<String, Boolean> windows = new ConcurrentHashMap<>();
    private volatile View shownOn;

    private Docking(App app) {
        this.app = app;
    }

    public static Docking on(App app) {
        return new Docking(Objects.requireNonNull(app, "app"));
    }

    public Docking style(Style style) { this.style = Objects.requireNonNull(style); return this; }

    /** The zones offered (all unless set). With {@link Style#TAB_STRIP} it is the center, as a tab. */
    public Docking zones(Zone first, Zone... more) { this.zones = EnumSet.of(first, more); return this; }

    public Docking keepFloatingWith(Key key) { this.key = Objects.requireNonNull(key); return this; }

    /** The highlight's fill and its outline, as CSS colours. */
    public Docking highlight(String fill, String outline) { this.color = fill; this.outline = outline; return this; }

    /** How near an edge (a fraction of the host's width or height, 0.25 unless set) its half is offered. */
    public Docking edge(double fraction) {
        if (fraction <= 0 || fraction >= 0.5) throw new IllegalArgumentException("an edge is a fraction above 0, below 0.5");
        this.edge = fraction;
        return this;
    }

    /** {@link Style#TAB_STRIP}: the strip's height at the top of a host, logical pixels (40 unless set). */
    public Docking tabStrip(double height) { this.strip = height; return this; }

    /** Which windows may be docked (all unless set). */
    public Docking dockable(Predicate<View> which) { this.dockable = Objects.requireNonNull(which); return this; }

    /** Which windows take others in (all unless set). */
    public Docking hosts(Predicate<View> which) { this.host = Objects.requireNonNull(which); return this; }

    /**
     * Whether the window dragged over is lit as a whole (true unless set): its frame in the {@link #frameColor} (Windows
     * 11), and a rim inside it, around all its page, in the outline's colour (on every Windows).
     */
    public Docking hostHighlight(boolean on) { this.hostHighlight = on; return this; }

    /** The colour a host's frame is lit in while a window is dragged over it (#3157d5 unless set). */
    public Docking frameColor(int red, int green, int blue) {
        WindowOptions.rgba(red, green, blue, 255);
        this.frame = new int[] {red, green, blue};
        return this;
    }

    /**
     * Whether docking says it worked with a settle (true unless set): the area it took pulses once, from a little
     * smaller to its size, and fades, in about a quarter of a second.
     */
    public Docking settle(boolean on) { this.settle = on; return this; }

    /** Whether docking says it worked with a sound too: a short, soft tick (off unless set). */
    public Docking sound(boolean on) { this.sound = on ? tick() : null; return this; }

    /** A sound of the app's own for a dock that worked: an MP3, WAV, Ogg or FLAC (see {@link App#playSound}). */
    public Docking sound(byte[] played) { this.sound = played == null ? null : played.clone(); return this; }

    /** A short, soft tick: 60 ms of a falling tone, quiet, as a WAV (16-bit mono, 44.1 kHz). */
    static byte[] tick() {
        int rate = 44_100, samples = rate * 60 / 1000;
        java.nio.ByteBuffer wav = java.nio.ByteBuffer.allocate(44 + samples * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        wav.put("RIFF".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(36 + samples * 2)
                .put("WAVEfmt ".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(16).putShort((short) 1).putShort((short) 1)
                .putInt(rate).putInt(rate * 2).putShort((short) 2).putShort((short) 16)
                .put("data".getBytes(java.nio.charset.StandardCharsets.US_ASCII)).putInt(samples * 2);
        for (int i = 0; i < samples; i++) {
            double t = i / (double) rate, fade = Math.exp(-t * 70);
            double tone = Math.sin(2 * Math.PI * (1200 - 4000 * t) * t);   // a little falling: a tick, not a beep
            wav.putShort((short) Math.round(tone * fade * 0.12 * Short.MAX_VALUE));
        }
        return wav.array();
    }

    /** Whether a hint says what letting go does (true unless set). */
    public Docking hints(boolean on) { this.hints = on; return this; }

    /**
     * The hint's words: for the zone it would go to (null: none there) and whether the key that keeps it floating is
     * held. Unless set: "Release to dock on the left · hold Ctrl to keep it floating", and the like.
     */
    public Docking hintText(java.util.function.BiFunction<Zone, Boolean, String> words) { this.hintText = Objects.requireNonNull(words); return this; }

    private String defaultHint(Zone zone, boolean held) {
        String keyName = switch (key) { case CTRL -> "Ctrl"; case SHIFT -> "Shift"; case ALT -> "Alt"; case NONE -> null; };
        if (held) return "Kept floating · let go of " + keyName + " to dock";
        String where = zone == null ? null : switch (zone) {
            case CENTER -> style == Style.TAB_STRIP ? "as a tab" : "here";
            case LEFT -> "on the left";
            case RIGHT -> "on the right";
            case TOP -> "at the top";
            case BOTTOM -> "at the bottom";
        };
        String first = where != null ? "Release to dock " + where
                : style == Style.GUIDES ? "Drop on a guide to dock" : style == Style.TAB_STRIP ? "Drop on the tab strip to dock" : null;
        if (first == null) return null;
        return keyName == null || where == null ? first : first + " · hold " + keyName + " to keep it floating";
    }

    /**
     * Whether the host grows by a panel docked at its side, so what was in it keeps its size (true unless set); false:
     * the panel takes its room from inside the host. A panel's size is the docked window's own across that side,
     * at most {@link #panelShare} of the host and at least {@link #smallestPanel}.
     */
    public Docking grow(boolean on) { this.grow = on; return this; }

    /** The most of a host a docked panel takes across its side (0.35 unless set). */
    public Docking panelShare(double fraction) {
        if (fraction <= 0 || fraction >= 1) throw new IllegalArgumentException("a share is a fraction above 0, below 1");
        this.share = fraction;
        return this;
    }

    /** The least a docked panel is across its side, logical pixels (160 unless set). */
    public Docking smallestPanel(double size) { this.smallest = size; return this; }

    /** What docking does: unless set, the view becomes a panel of the host (or its whole page, at the center). */
    public Docking onDock(Consumer<Drop> then) { this.onDock = Objects.requireNonNull(then); return this; }

    /** Starts watching drags of windows. */
    public synchronized Docking start() {
        if (subscription == null) subscription = app.subscribe(WindowMove.class, this::moved);
        return this;
    }

    @Override
    public synchronized void close() {
        if (subscription != null) subscription.close();
        subscription = null;
        if (leaving != null) leaving.close();
        leaving = null;
        overlays.values().forEach(View::close);
        overlays.clear();
    }

    /** Whether a view is a window (not an embedded view, whose moves are inside its window): known once, kept. */
    private boolean window(View view) {
        return windows.computeIfAbsent(view.id(), id -> {
            try {
                view.geometry().join();
                return true;
            } catch (RuntimeException embedded) {
                return false;
            }
        });
    }

    private boolean keyHeld(WindowMove move) {
        return switch (key) {
            case CTRL -> move.ctrl();
            case SHIFT -> move.shift();
            case ALT -> move.alt();
            case NONE -> false;
        };
    }

    void moved(WindowMove move) {   // package-private: tests drive it as a drag would
        View dragged = app.view(move.view());
        if (!window(dragged) || !dockable.test(dragged)) return;
        Optional<View> under = app.windowAt(move.cursorX(), move.cursorY(), dragged).join().filter(h -> !h.equals(dragged) && host.test(h));
        Optional<Drop> drop = under.flatMap(h -> dropAt(dragged, h, move));
        if (move.phase() == WindowMove.Phase.ENDED) {
            hide();
            drop.ifPresent(d -> {
                onDock.accept(d);
                docked(d);
            });
            return;
        }
        if (under.isEmpty()) {
            hide();
            return;
        }
        show(under.get(), drop.orElse(null), keyHeld(move));
    }

    private Optional<Drop> dropAt(View dragged, View host, WindowMove move) {
        if (keyHeld(move)) return Optional.empty();   // kept floating
        Map<?, ?> client = (Map<?, ?>) host.client().join();
        double scale = ((Number) client.get("scale")).doubleValue();
        double w = ((Number) client.get("width")).doubleValue() / scale, h = ((Number) client.get("height")).doubleValue() / scale;
        double x = (move.cursorX() - ((Number) client.get("x")).doubleValue()) / scale;
        double y = (move.cursorY() - ((Number) client.get("y")).doubleValue()) / scale;
        return zoneAt(style, zones, w, h, x, y, edge, strip).map(zone -> {
            double[] b = zone == Zone.CENTER || style == Style.TAB_STRIP ? bounds(zone, w, h, style == Style.TAB_STRIP ? strip : 0)
                    : edgeBounds(zone, w, h, panelSize(dragged, zone, w, h));
            return new Drop(dragged, host, zone, b[0], b[1], b[2], b[3]);
        });
    }

    /** The guides' size and their distance from the middle, logical pixels. */
    static final double GUIDE = 40, GUIDE_STEP = 48;

    /**
     * The zone at ({@code x}, {@code y}) in a host {@code w} × {@code h} (logical pixels): what a window let go there
     * would take. Empty where none is offered there.
     */
    static Optional<Zone> zoneAt(Style style, Set<Zone> zones, double w, double h, double x, double y, double edge, double strip) {
        if (x < 0 || y < 0 || x >= w || y >= h) return Optional.empty();
        Zone zone = switch (style) {
            case TAB_STRIP -> y < strip ? Zone.CENTER : null;
            case GUIDES -> {
                double cx = w / 2, cy = h / 2;
                Zone hit = null;
                for (Zone z : Zone.values()) {
                    double[] g = guide(z, cx, cy);
                    if (x >= g[0] && x < g[0] + GUIDE && y >= g[1] && y < g[1] + GUIDE) hit = z;
                }
                yield hit;
            }
            case TARGET_AREA -> {
                double fx = x / w, fy = y / h;
                // the nearest edge within reach, else the middle
                double left = fx, right = 1 - fx, top = fy, bottom = 1 - fy;
                double nearest = Math.min(Math.min(left, right), Math.min(top, bottom));
                if (nearest >= edge) yield Zone.CENTER;
                yield nearest == left ? Zone.LEFT : nearest == right ? Zone.RIGHT : nearest == top ? Zone.TOP : Zone.BOTTOM;
            }
        };
        if (zone == null) return Optional.empty();
        if (style == Style.TAB_STRIP) return Optional.of(zone);
        return zones.contains(zone) ? Optional.of(zone) : style == Style.TARGET_AREA && zones.contains(Zone.CENTER) ? Optional.of(Zone.CENTER) : Optional.empty();
    }

    /** A guide's top-left corner, for a compass around (cx, cy). */
    static double[] guide(Zone zone, double cx, double cy) {
        double half = GUIDE / 2;
        return switch (zone) {
            case CENTER -> new double[] {cx - half, cy - half};
            case LEFT -> new double[] {cx - half - GUIDE_STEP, cy - half};
            case RIGHT -> new double[] {cx - half + GUIDE_STEP, cy - half};
            case TOP -> new double[] {cx - half, cy - half - GUIDE_STEP};
            case BOTTOM -> new double[] {cx - half, cy - half + GUIDE_STEP};
        };
    }

    /** A docked window's panel across its side: its own size there, at most the share of the host, at least the least. */
    private double panelSize(View dragged, Zone side, double w, double h) {
        Geometry g = dragged.geometry().join();
        double own = side == Zone.LEFT || side == Zone.RIGHT ? g.width() : g.height();
        double across = side == Zone.LEFT || side == Zone.RIGHT ? w : h;
        return panel(own, across, share, smallest);
    }

    /** A panel's size: {@code own}, at most {@code share} of {@code across}, at least {@code smallest} (if it fits). */
    static double panel(double own, double across, double share, double smallest) {
        return Math.max(Math.min(smallest, across), Math.min(own, across * share));
    }

    /** A strip {@code size} deep along a side of a host {@code w} × {@code h}: x, y, width, height. */
    static double[] edgeBounds(Zone side, double w, double h, double size) {
        return switch (side) {
            case LEFT -> new double[] {0, 0, size, h};
            case RIGHT -> new double[] {w - size, 0, size, h};
            case TOP -> new double[] {0, 0, w, size};
            case BOTTOM -> new double[] {0, h - size, w, size};
            case CENTER -> new double[] {0, 0, w, h};
        };
    }

    /**
     * Where each of a host's panels goes, and what is left for the host's own page, in a host {@code w} × {@code h}:
     * left and right panels the full height, each next one inward; top and bottom ones between them. The bounds of
     * each panel in order, then the room left (x, y, width, height).
     */
    static java.util.List<double[]> arrange(java.util.List<Panel> panels, double w, double h) {
        double left = 0, right = w, top = 0, bottom = h;
        java.util.List<double[]> out = new java.util.ArrayList<>();
        for (Panel p : panels) {
            if (p.side() == Zone.LEFT) { out.add(new double[] {left, 0, p.size(), h}); left += p.size(); }
            else if (p.side() == Zone.RIGHT) { out.add(new double[] {right - p.size(), 0, p.size(), h}); right -= p.size(); }
            else out.add(null);
        }
        for (int i = 0; i < panels.size(); i++) {
            Panel p = panels.get(i);
            if (p.side() == Zone.TOP) { out.set(i, new double[] {left, top, right - left, p.size()}); top += p.size(); }
            else if (p.side() == Zone.BOTTOM) { out.set(i, new double[] {left, bottom - p.size(), right - left, p.size()}); bottom -= p.size(); }
            else if (p.side() == Zone.CENTER) out.set(i, null);
        }
        for (int i = 0; i < panels.size(); i++) if (panels.get(i).side() == Zone.CENTER) out.set(i, new double[] {left, top, right - left, bottom - top});
        out.add(new double[] {left, top, right - left, bottom - top});
        return out;
    }

    /** The host grown by a panel docked at a side (left and top grow outward, moving the window), or shrunk back. */
    private void resizeHost(View host, Zone side, double by) {
        if (!grow || side == Zone.CENTER) return;
        Geometry g = host.geometry().join();
        switch (side) {
            case LEFT -> { host.setPosition(g.x() - by, g.y()).join(); host.setSize(g.width() + by, g.height()).join(); }
            case RIGHT -> host.setSize(g.width() + by, g.height()).join();
            case TOP -> { host.setPosition(g.x(), g.y() - by).join(); host.setSize(g.width(), g.height() + by).join(); }
            case BOTTOM -> host.setSize(g.width(), g.height() + by).join();
            default -> { }
        }
    }

    /** The default docking: the view a panel of the host at its side (the host grown for it), or its page at the center. */
    void layOut(Drop drop) {
        View host = drop.host();
        Zone side = drop.zone();
        double size = side == Zone.LEFT || side == Zone.RIGHT ? drop.width() : side == Zone.CENTER ? 0 : drop.height();
        java.util.List<Panel> list = panels.computeIfAbsent(host.id(), id -> new java.util.concurrent.CopyOnWriteArrayList<>());
        list.add(new Panel(drop.dragged(), side, size));
        resizeHost(host, side, size);
        watchLeaving();
        place(host, list, drop.dragged());
    }

    /** Each panel where it goes, the one just docked taken in; the host's page told its room. */
    private void place(View host, java.util.List<Panel> list, View taken) {
        Map<?, ?> client = (Map<?, ?>) host.client().join();
        double scale = ((Number) client.get("scale")).doubleValue();
        double w = ((Number) client.get("width")).doubleValue() / scale, h = ((Number) client.get("height")).doubleValue() / scale;
        java.util.List<double[]> where = arrange(list, w, h);
        for (int i = 0; i < list.size(); i++) {
            double[] b = where.get(i);
            View v = list.get(i).view();
            if (v.equals(taken)) host.adopt(v, b[0], b[1], b[2], b[3]).join();
            else v.place(b[0], b[1], b[2], b[3]).join();
        }
        double[] room = where.getLast();
        host.eval("(() => { const s = document.documentElement.style;"
                + " s.setProperty('--swaswa-dock-left', '" + room[0] + "px'); s.setProperty('--swaswa-dock-top', '" + room[1] + "px');"
                + " s.setProperty('--swaswa-dock-right', '" + (w - room[0] - room[2]) + "px'); s.setProperty('--swaswa-dock-bottom', '" + (h - room[1] - room[3]) + "px'); })()");
    }

    /** A panel that leaves its host (torn off, closed): the host shrinks back, the others close up. */
    private synchronized void watchLeaving() {
        if (leaving != null) return;
        leaving = app.subscribe(WebViewEvent.class, e -> {
            if (!(e instanceof ViewReparented || e instanceof Closed)) return;
            if (e instanceof ViewReparented r && r.host() != null) return;   // docked, not leaving
            for (Map.Entry<String, java.util.List<Panel>> entry : panels.entrySet()) {
                for (Panel p : entry.getValue()) {
                    if (!p.view().id().equals(e.view())) continue;
                    entry.getValue().remove(p);
                    if (e instanceof Closed && e.view().equals(entry.getKey())) continue;
                    try {
                        View host = app.view(entry.getKey());
                        resizeHost(host, p.side(), -p.size());
                        place(host, entry.getValue(), null);
                    } catch (RuntimeException hostGone) {
                        panels.remove(entry.getKey());
                    }
                }
            }
            if (e instanceof Closed) panels.remove(e.view());
        });
    }

    /** Where a zone puts a view in a host {@code w} × {@code h}: x, y, width, height (below a tab strip, if any). */
    static double[] bounds(Zone zone, double w, double h, double strip) {
        double top = strip, height = h - strip;
        return switch (zone) {
            case CENTER -> new double[] {0, top, w, height};
            case LEFT -> new double[] {0, top, w / 2, height};
            case RIGHT -> new double[] {w / 2, top, w / 2, height};
            case TOP -> new double[] {0, top, w, height / 2};
            case BOTTOM -> new double[] {0, top + height / 2, w, height / 2};
        };
    }

    // ---- the highlight: a transparent view above all the host has ----

    private static final String OVERLAY = """
            <!doctype html><meta charset=utf-8>
            <style>html, body { margin: 0; height: 100%; background: transparent; overflow: hidden; pointer-events: none }
              .area { position: absolute; box-sizing: border-box; border-radius: 6px; transition: all 90ms ease-out }
              .guide { position: absolute; width: 40px; height: 40px; box-sizing: border-box; border-radius: 6px;
                background: rgba(255, 255, 255, 0.92); box-shadow: 0 2px 10px rgba(0, 0, 0, 0.25) }
              .guide::after { content: ''; position: absolute; inset: 8px; border-radius: 3px; opacity: .55 }
              .strip { position: absolute; left: 0; right: 0; top: 0; box-sizing: border-box }
              .settle { position: absolute; box-sizing: border-box; border-radius: 6px; animation: settle 260ms ease-out forwards }
              @keyframes settle { from { transform: scale(.96); opacity: .9 } 60% { transform: scale(1); opacity: .7 } to { transform: scale(1); opacity: 0 } }
              .host { position: absolute; inset: 0; box-sizing: border-box; border-radius: 4px;
                box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.55) }
              .hint { position: absolute; transform: translate(-50%, 0); padding: 5px 10px; border-radius: 6px; white-space: nowrap;
                font: 12px system-ui, sans-serif; color: #fff; background: rgba(20, 22, 28, 0.86); box-shadow: 0 2px 8px rgba(0,0,0,.3) }</style>
            <div id=root></div>
            <script>
              window.__dock = (s) => {
                const root = document.getElementById('root'); root.textContent = '';
                const add = (cls, css) => { const d = document.createElement('div'); d.className = cls; Object.assign(d.style, css); root.append(d); return d; };
                if (s.settle) add('settle', { left: s.settle[0] + 'px', top: s.settle[1] + 'px', width: s.settle[2] + 'px', height: s.settle[3] + 'px',
                  background: s.fill, border: '2px solid ' + s.outline });
                if (s.host) add('host', { border: '3px solid ' + s.outline });
                if (s.area) add('area', { left: s.area[0] + 'px', top: s.area[1] + 'px', width: s.area[2] + 'px', height: s.area[3] + 'px',
                  background: s.fill, border: '2px solid ' + s.outline });
                if (s.style === 'TAB_STRIP') add('strip', { height: s.strip + 'px', borderBottom: '3px solid ' + (s.zone ? s.outline : 'transparent'),
                  background: s.zone ? s.fill : 'transparent' });
                if (s.hint) {
                  const at = s.area ? [s.area[0] + s.area[2] / 2, s.area[1] + s.area[3] / 2 + 28]
                    : s.style === 'TAB_STRIP' ? [innerWidth / 2, s.strip + 8] : [innerWidth / 2, innerHeight / 2 + 96];
                  const h = add('hint', { left: at[0] + 'px', top: at[1] + 'px' }); h.textContent = s.hint;
                }
                for (const g of s.guides || []) {
                  const d = add('guide', { left: g.x + 'px', top: g.y + 'px', outline: g.on ? '2px solid ' + s.outline : 'none' });
                  d.style.setProperty('--c', s.outline);
                  d.innerHTML = '<div style="position:absolute;inset:8px;border-radius:3px;background:' + s.outline + ';opacity:' + (g.on ? 0.9 : 0.4) + '"></div>';
                }
              };
            </script>""";

    private void show(View host, Drop drop, boolean held) {
        View previous = shownOn;
        if (previous != null && !previous.equals(host)) hide();
        Map<?, ?> client = (Map<?, ?>) host.client().join();
        double scale = ((Number) client.get("scale")).doubleValue();
        double w = ((Number) client.get("width")).doubleValue() / scale, h = ((Number) client.get("height")).doubleValue() / scale;
        View overlay = overlays.computeIfAbsent(host.id(), id -> {
            View made = host.embed(WindowOptions.html(OVERLAY).transparent(true).backgroundColor(0, 0, 0, 0), 0, 0, w, h).join();
            ViewMethodsWait.page(made);
            return made;
        });
        overlay.place(0, 0, w, h).join();
        overlay.show();
        overlay.toFront();
        shownOn = host;
        Map<String, Object> said = new java.util.LinkedHashMap<>();
        said.put("style", style.name());
        said.put("fill", color);
        said.put("outline", outline);
        said.put("strip", strip);
        said.put("zone", drop == null ? null : drop.zone().name());
        said.put("host", hostHighlight);
        if (hostHighlight && !host.equals(framed)) {
            unframe();
            framed = host;
            host.setBorderColor(frame[0], frame[1], frame[2]).exceptionally(noBorderColours -> null);   // Windows 10: the rim only
        }
        said.put("hint", hints ? hintText.apply(drop == null ? null : drop.zone(), held) : null);
        app.pageState(host.id(), "dock-target", drop == null ? Boolean.FALSE : drop.zone().name().toLowerCase(java.util.Locale.ROOT));
        if (drop != null && style != Style.TAB_STRIP) said.put("area", java.util.List.of(drop.x(), drop.y(), drop.width(), drop.height()));
        if (style == Style.GUIDES) {
            java.util.List<Map<String, Object>> guides = new java.util.ArrayList<>();
            for (Zone z : zones) {
                double[] g = guide(z, w / 2, h / 2);
                guides.add(Map.of("x", g[0], "y", g[1], "on", drop != null && drop.zone() == z));
            }
            said.put("guides", guides);
        }
        overlay.eval("window.__dock && window.__dock(" + Json.write(said) + ")");
    }

    /** It worked: the settle in the area it took, above it, and the sound, as set. */
    private void docked(Drop drop) {
        if (sound != null) app.playSound(sound).exceptionally(noSound -> null);
        if (!settle) return;
        View overlay = overlays.get(drop.host().id());
        if (overlay == null) return;
        Map<String, Object> said = new java.util.LinkedHashMap<>();
        said.put("settle", java.util.List.of(drop.x(), drop.y(), drop.width(), drop.height()));
        said.put("fill", color);
        said.put("outline", outline);
        overlay.show();
        overlay.toFront();   // above the view it took in
        overlay.eval("window.__dock && window.__dock(" + Json.write(said) + ")");
        java.util.concurrent.CompletableFuture.delayedExecutor(320, java.util.concurrent.TimeUnit.MILLISECONDS).execute(() -> {
            if (shownOn == null) {
                overlay.eval("window.__dock && window.__dock({})");
                overlay.hide();
            }
        });
    }

    private void unframe() {
        View was = framed;
        framed = null;
        if (was != null) was.clearBorderColor().exceptionally(gone -> null);
    }

    private void hide() {
        View on = shownOn;
        shownOn = null;
        if (on == null) return;
        app.pageState(on.id(), "dock-target", false);
        unframe();
        View overlay = overlays.get(on.id());
        if (overlay != null) {
            overlay.eval("window.__dock && window.__dock({})");
            overlay.hide();
        }
    }

    /** What the highlight's page needs before it is drawn on: its script there. */
    private static final class ViewMethodsWait {
        static void page(View view) {
            for (int i = 0; i < 100; i++) {
                if (Boolean.TRUE.equals(view.eval("typeof window.__dock === 'function'").join())) return;
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}
