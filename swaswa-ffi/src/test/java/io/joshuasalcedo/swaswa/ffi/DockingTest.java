package io.joshuasalcedo.swaswa.ffi;

import static io.joshuasalcedo.swaswa.ffi.ViewMethodsTest.until;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.foreign.MemorySegment;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * Docking: where each style docks a window let go at a point, the highlight drawn above the host while it is dragged,
 * docked where it was let go, and kept floating with the key held. The real mouse in the last test
 * (-Dswaswa.mouse=true).
 */
@Timeout(120)
class DockingTest {

    static final Set<Docking.Zone> ALL = EnumSet.allOf(Docking.Zone.class);

    static Optional<Docking.Zone> at(Docking.Style style, double x, double y) {
        return Docking.zoneAt(style, ALL, 800, 600, x, y, 0.25, 40);
    }

    @Test
    void theTargetAreaIsTheNearestEdgesHalfOrAllOfIt() {
        assertEquals(Optional.of(Docking.Zone.LEFT), at(Docking.Style.TARGET_AREA, 50, 300));
        assertEquals(Optional.of(Docking.Zone.RIGHT), at(Docking.Style.TARGET_AREA, 760, 300));
        assertEquals(Optional.of(Docking.Zone.TOP), at(Docking.Style.TARGET_AREA, 400, 40));
        assertEquals(Optional.of(Docking.Zone.BOTTOM), at(Docking.Style.TARGET_AREA, 400, 580));
        assertEquals(Optional.of(Docking.Zone.CENTER), at(Docking.Style.TARGET_AREA, 400, 300), "the middle: all of it");
        assertEquals(Optional.empty(), at(Docking.Style.TARGET_AREA, 900, 300), "outside: nothing");
        // only some zones offered: the others fall to the middle
        assertEquals(Optional.of(Docking.Zone.CENTER), Docking.zoneAt(Docking.Style.TARGET_AREA, EnumSet.of(Docking.Zone.CENTER), 800, 600, 50, 300, 0.25, 40));
        assertEquals(Optional.empty(), Docking.zoneAt(Docking.Style.TARGET_AREA, EnumSet.of(Docking.Zone.RIGHT), 800, 600, 50, 300, 0.25, 40));
        assertEquals(Optional.of(Docking.Zone.LEFT), Docking.zoneAt(Docking.Style.TARGET_AREA, ALL, 800, 600, 150, 300, 0.2, 40), "a wider edge: nearer counts");
        assertEquals(Optional.of(Docking.Zone.CENTER), Docking.zoneAt(Docking.Style.TARGET_AREA, ALL, 800, 600, 150, 300, 0.15, 40), "a narrower one: the middle");
    }

    @Test
    void guidesDockOnlyOnAGuide() {
        // a compass around the middle (400, 300): 40 px guides, 48 apart
        assertEquals(Optional.of(Docking.Zone.CENTER), at(Docking.Style.GUIDES, 400, 300));
        assertEquals(Optional.of(Docking.Zone.LEFT), at(Docking.Style.GUIDES, 352, 300));
        assertEquals(Optional.of(Docking.Zone.RIGHT), at(Docking.Style.GUIDES, 448, 300));
        assertEquals(Optional.of(Docking.Zone.TOP), at(Docking.Style.GUIDES, 400, 252));
        assertEquals(Optional.of(Docking.Zone.BOTTOM), at(Docking.Style.GUIDES, 400, 348));
        assertEquals(Optional.empty(), at(Docking.Style.GUIDES, 50, 300), "by an edge but not on a guide: nothing");
        assertEquals(Optional.empty(), at(Docking.Style.GUIDES, 376, 276), "between guides: nothing");
    }

    @Test
    void aTabStripDocksOnlyOverTheStripAsATab() {
        assertEquals(Optional.of(Docking.Zone.CENTER), at(Docking.Style.TAB_STRIP, 300, 20));
        assertEquals(Optional.empty(), at(Docking.Style.TAB_STRIP, 300, 60), "below the strip: nothing");
        assertArrayEquals(new double[] {0, 40, 800, 560}, Docking.bounds(Docking.Zone.CENTER, 800, 600, 40), "below the strip, all of it");
    }

    @Test
    void eachZoneIsItsPartOfTheHost() {
        assertArrayEquals(new double[] {0, 0, 800, 600}, Docking.bounds(Docking.Zone.CENTER, 800, 600, 0));
        assertArrayEquals(new double[] {0, 0, 400, 600}, Docking.bounds(Docking.Zone.LEFT, 800, 600, 0));
        assertArrayEquals(new double[] {400, 0, 400, 600}, Docking.bounds(Docking.Zone.RIGHT, 800, 600, 0));
        assertArrayEquals(new double[] {0, 0, 800, 300}, Docking.bounds(Docking.Zone.TOP, 800, 600, 0));
        assertArrayEquals(new double[] {0, 300, 800, 300}, Docking.bounds(Docking.Zone.BOTTOM, 800, 600, 0));
    }

    /** A drag as the system reports it: the pointer at (x, y) of the host's page area, logical pixels. */
    static WindowMove over(View dragged, View host, WindowMove.Phase phase, double x, double y, boolean ctrl) {
        Map<?, ?> client = (Map<?, ?>) host.client().join();
        double scale = ((Number) client.get("scale")).doubleValue();
        int cx = (int) Math.round(((Number) client.get("x")).doubleValue() + x * scale);
        int cy = (int) Math.round(((Number) client.get("y")).doubleValue() + y * scale);
        return new WindowMove(dragged.id(), phase, 0, 0, 400, 300, cx, cy, ctrl, false, false);
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void draggedOverAHostItShowsWhereThenDocksThere() throws Exception {
        try (App app = App.start()) {
            BlockingQueue<ViewReparented> docked = new LinkedBlockingQueue<>();
            app.subscribe(ViewReparented.class, docked::add);
            View host = app.open(WindowOptions.html("<body style='background:#dde'>").title("swaswa test: dock target").size(800, 600).position(100, 80)).join();
            View panel = DockTest.stateful(app, "swaswa test: dock me", 1000);
            View inside = host.embed(WindowOptions.html("<p>an embedded view of its own</p>"), 0, 0, 800, 600).join();
            panel.focus().join();   // a window being dragged is above all
            Thread.sleep(300);
            BlockingQueue<Docking.Drop> drops = new LinkedBlockingQueue<>();
            Docking docking = Docking.on(app).onDock(drop -> {
                drops.add(drop);
                drop.host().adopt(drop.dragged(), drop.x(), drop.y(), drop.width(), drop.height()).join();
            });

            // over its left edge: the highlight shows, above what the host has inside it
            int viewsBefore = app.views().join().size();
            docking.moved(over(panel, host, WindowMove.Phase.MOVING, 40, 300, false));
            assertEquals(viewsBefore + 1, app.views().join().size(), "the highlight's view, in the host");
            View overlay = app.views().join().stream().map(v -> app.view(v.id()))
                    .filter(v -> !v.equals(host) && !v.equals(panel) && !v.equals(inside)).findFirst().orElseThrow();
            assertEquals(1L, ((Number) until(() -> overlay.eval("document.querySelectorAll('.area').length").join(), n -> ((Number) n).intValue() == 1)).longValue(),
                    "the area it would take, drawn");
            assertEquals(280, ((Number) overlay.eval("parseFloat(document.querySelector('.area').style.width)").join()).doubleValue(), 1,
                    "a panel's room: its own 400, at most 35% of the host's 800");

            // Ctrl held: kept floating, nothing shown as its place
            docking.moved(over(panel, host, WindowMove.Phase.MOVING, 40, 300, true));
            assertEquals(0L, ((Number) overlay.eval("document.querySelectorAll('.area').length").join()).longValue(), "Ctrl: no place shown");
            docking.moved(over(panel, host, WindowMove.Phase.ENDED, 40, 300, true));
            assertNull(drops.poll(500, TimeUnit.MILLISECONDS), "let go with Ctrl: not docked");
            assertNotEquals(0, Win32.window("swaswa test: dock me", 0).address(), "still a window");

            // let go there without it: docked into the left half
            docking.moved(over(panel, host, WindowMove.Phase.MOVING, 40, 300, false));
            docking.moved(over(panel, host, WindowMove.Phase.ENDED, 40, 300, false));
            Docking.Drop drop = drops.poll(5, TimeUnit.SECONDS);
            assertNotNull(drop, "docked");
            assertEquals(Docking.Zone.LEFT, drop.zone());
            assertEquals(List.of(0.0, 0.0, 280.0), List.of(drop.x(), drop.y(), drop.width()), "a panel at the left");
            assertEquals(new ViewReparented(panel.id(), host.id()), docked.poll(10, TimeUnit.SECONDS));
            DockTest.stillTheSamePage(panel);
            assertEquals(0L, ((Number) overlay.eval("document.querySelectorAll('.area').length").join()).longValue(), "the highlight gone with the drag");
            docking.close();
        }
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void guidesAreDrawnAndTheOneUnderThePointerLit() throws Exception {
        try (App app = App.start()) {
            View host = app.open(WindowOptions.html("<p>h</p>").title("swaswa test: guides").size(800, 600).position(100, 80)).join();
            View panel = DockTest.stateful(app, "swaswa test: guided", 1000);
            panel.focus().join();
            Thread.sleep(300);
            Docking docking = Docking.on(app).style(Docking.Style.GUIDES).zones(Docking.Zone.CENTER, Docking.Zone.LEFT, Docking.Zone.RIGHT);
            Map<?, ?> client = (Map<?, ?>) host.client().join();
            double scale = ((Number) client.get("scale")).doubleValue();
            double w = ((Number) client.get("width")).doubleValue() / scale, h = ((Number) client.get("height")).doubleValue() / scale;
            docking.moved(over(panel, host, WindowMove.Phase.MOVING, w / 2 - 48, h / 2, false));   // on the left guide
            View overlay = app.views().join().stream().map(v -> app.view(v.id())).filter(v -> !v.equals(host) && !v.equals(panel)).findFirst().orElseThrow();
            assertEquals(3, ((Number) until(() -> overlay.eval("document.querySelectorAll('.guide').length").join(), n -> ((Number) n).intValue() == 3)).intValue(),
                    "the three guides offered");
            assertEquals(1, ((Number) overlay.eval("document.querySelectorAll('.area').length").join()).intValue(), "the left half lit");
            docking.close();
        }
    }

    @Test
    void theTickIsShortSoftAndASound() throws Exception {
        byte[] tick = Docking.tick();
        assertEquals("RIFF", new String(tick, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
        assertEquals("WAVE", new String(tick, 8, 4, java.nio.charset.StandardCharsets.US_ASCII));
        java.nio.ByteBuffer pcm = java.nio.ByteBuffer.wrap(tick, 44, tick.length - 44).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        int samples = (tick.length - 44) / 2, loudest = 0;
        for (int i = 0; i < samples; i++) loudest = Math.max(loudest, Math.abs(pcm.getShort()));
        assertEquals(60, samples * 1000 / 44_100, 1, "60 ms");
        assertTrue(loudest <= 0.12 * Short.MAX_VALUE + 1 && loudest > 0, "quiet: at most 12% of full: " + loudest);
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void aDockThatWorkedSettlesAndCanTick() throws Exception {
        try (App app = App.start()) {
            assertNull(app.playSound(Docking.tick()).get(10, TimeUnit.SECONDS), "the tick is a sound the system plays");
            View host = app.open(WindowOptions.html("<p>h</p>").title("swaswa test: settle").size(800, 600).position(100, 80)).join();
            View panel = DockTest.stateful(app, "swaswa test: settled", 1000);
            panel.focus().join();
            Docking docking = Docking.on(app).sound(true);

            // let go with Ctrl first: nothing docked, nothing settles
            docking.moved(over(panel, host, WindowMove.Phase.MOVING, 40, 300, false));
            View overlay = FeedbackTest.overlayOf(app, host, panel);
            docking.moved(over(panel, host, WindowMove.Phase.ENDED, 40, 300, true));
            Thread.sleep(150);
            assertEquals(0, ((Number) overlay.eval("document.querySelectorAll('.settle').length").join()).intValue(), "not docked: no settle");

            docking.moved(over(panel, host, WindowMove.Phase.MOVING, 40, 300, false));
            docking.moved(over(panel, host, WindowMove.Phase.ENDED, 40, 300, false));
            assertEquals(1, ((Number) until(() -> overlay.eval("document.querySelectorAll('.settle').length").join(), n -> ((Number) n).intValue() == 1)).intValue(),
                    "docked: the area it took settles");
            assertEquals(280, ((Number) overlay.eval("parseFloat(document.querySelector('.settle').style.width)").join()).doubleValue(), 1, "the panel it took");
            assertEquals("settle", overlay.eval("getComputedStyle(document.querySelector('.settle')).animationName").join());
            assertEquals(0, ((Number) until(() -> overlay.eval("document.querySelectorAll('.settle').length").join(), n -> ((Number) n).intValue() == 0)).intValue(),
                    "and is gone in a moment");
            docking.close();
        }
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void noSettleWhenOff() throws Exception {
        try (App app = App.start()) {
            View host = app.open(WindowOptions.html("<p>h</p>").title("swaswa test: no settle").size(800, 600).position(100, 80)).join();
            View panel = DockTest.stateful(app, "swaswa test: unsettled", 1000);
            panel.focus().join();
            Docking docking = Docking.on(app).settle(false);
            docking.moved(over(panel, host, WindowMove.Phase.MOVING, 40, 300, false));
            View overlay = FeedbackTest.overlayOf(app, host, panel);
            docking.moved(over(panel, host, WindowMove.Phase.ENDED, 40, 300, false));
            Thread.sleep(200);
            assertEquals(0, ((Number) overlay.eval("document.querySelectorAll('.settle').length").join()).intValue());
            docking.close();
        }
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    @EnabledIfSystemProperty(named = "swaswa.mouse", matches = "true")
    void aRealDragDocksAndCtrlKeepsItFloating() throws Exception {
        try (App app = App.start()) {
            BlockingQueue<ViewReparented> docked = new LinkedBlockingQueue<>();
            app.subscribe(ViewReparented.class, docked::add);
            View host = app.open(WindowOptions.html("<body style='background:#dde'>").title("swaswa test: real dock").size(800, 600).position(100, 80)).join();
            View panel = DockTest.stateful(app, "swaswa test: real panel", 1000);
            try (Docking docking = Docking.on(app).start()) {
                MemorySegment hp = Win32.window("swaswa test: real panel"), hh = Win32.window("swaswa test: real host".replace("real host", "real dock"));
                int[] host_ = Win32.rect(hh);
                int[] leftEdge = {host_[0] + 40, (host_[1] + host_[3]) / 2};

                // with Ctrl held: it stays a window
                ViewMethodsTest.front(panel);
                Thread.sleep(300);
                int[] p = Win32.rect(hp);
                Win32.key(0x11, true);   // VK_CONTROL
                Win32.drag(p[0] + 80, p[1] + 12, leftEdge[0], leftEdge[1]);
                Win32.key(0x11, false);
                assertNull(docked.poll(1500, TimeUnit.MILLISECONDS), "Ctrl held: not docked");

                // without: into the left half (from beside the host again: Ctrl left it on the host's edge)
                panel.setPosition(1000, 100).join();
                Thread.sleep(300);
                ViewMethodsTest.front(panel);
                Thread.sleep(300);
                p = Win32.rect(hp);
                Win32.drag(p[0] + 80, p[1] + 12, leftEdge[0], leftEdge[1]);
                assertEquals(new ViewReparented(panel.id(), host.id()), docked.poll(10, TimeUnit.SECONDS), "docked");
                DockTest.stillTheSamePage(panel);
                assertEquals(1080, until(() -> host.geometry().join().width(), w -> Math.abs(w - 1080) < 2), 2, "the host grew by the panel");
                assertEquals(280, until(() -> DockTest.innerWidth(panel), w -> Math.abs(w - 280) < 3), 3, "the panel small, its own size");
            }
        }
    }
}
