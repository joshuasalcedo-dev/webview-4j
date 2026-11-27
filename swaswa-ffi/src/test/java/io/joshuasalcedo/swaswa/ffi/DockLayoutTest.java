package io.joshuasalcedo.swaswa.ffi;

import static io.joshuasalcedo.swaswa.ffi.ViewMethodsTest.until;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * Docked panels: a panel takes its own size (not half), the host grows by it so what was there keeps its size,
 * several lay out together, and the host shrinks back when one leaves; the host's page is told the room it has.
 */
@Timeout(120)
class DockLayoutTest {

    @Test
    void aPanelIsItsOwnSizeWithinBounds() {
        assertEquals(280, Docking.panel(400, 800, 0.35, 160), "its own 400, at most 35% of 800");
        assertEquals(200, Docking.panel(200, 800, 0.35, 160), "its own, when it fits");
        assertEquals(160, Docking.panel(100, 800, 0.35, 160), "at least 160");
        assertEquals(120, Docking.panel(100, 120, 0.35, 160), "not more than the host has");
    }

    @Test
    void panelsArrangeAroundWhatIsLeft() {
        List<Docking.Panel> panels = List.of(
                new Docking.Panel(null, Docking.Zone.LEFT, 200),
                new Docking.Panel(null, Docking.Zone.RIGHT, 150),
                new Docking.Panel(null, Docking.Zone.TOP, 100),
                new Docking.Panel(null, Docking.Zone.LEFT, 50));
        List<double[]> where = Docking.arrange(panels, 1000, 600);
        assertArrayEquals(new double[] {0, 0, 200, 600}, where.get(0), "left: the full height");
        assertArrayEquals(new double[] {850, 0, 150, 600}, where.get(1), "right: the full height");
        assertArrayEquals(new double[] {250, 0, 600, 100}, where.get(2), "top: between the sides");
        assertArrayEquals(new double[] {200, 0, 50, 600}, where.get(3), "a second left: inward of the first");
        assertArrayEquals(new double[] {250, 100, 600, 500}, where.get(4), "the room left for the host's own");
    }

    static double cssVar(View host, String name) {
        Object v = host.eval("getComputedStyle(document.documentElement).getPropertyValue('--swaswa-dock-" + name + "')").join();
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? 0 : Double.parseDouble(s.replace("px", ""));
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void theHostGrowsForAPanelAndShrinksWhenItLeaves() throws Exception {
        try (App app = App.start()) {
            View host = app.open(WindowOptions.html("<body style='margin:0;background:#dde'>").title("swaswa test: growing host").size(800, 600).position(500, 100)).join();
            View left = DockTest.stateful(app, "swaswa test: left panel", 1500);
            View right = app.open(WindowOptions.html("<p>r</p>").title("swaswa test: right panel").size(300, 400).position(1500, 500)).join();
            Docking docking = Docking.on(app);

            left.focus().join();
            docking.moved(DockingTest.over(left, host, WindowMove.Phase.MOVING, 40, 300, false));
            docking.moved(DockingTest.over(left, host, WindowMove.Phase.ENDED, 40, 300, false));
            Geometry grown = until(() -> host.geometry().join(), g -> Math.abs(g.width() - 1080) < 2);
            assertEquals(1080, grown.width(), 2, "grown by the panel: 800 + 280");
            assertEquals(500 - 280, grown.x(), 2, "to the left, where it docked");
            assertEquals(280, until(() -> DockTest.innerWidth(left), w -> Math.abs(w - 280) < 3), 3, "the panel: small, its own size");
            DockTest.stillTheSamePage(left);
            assertEquals(280, until(() -> cssVar(host, "left"), v -> Math.abs(v - 280) < 1), 1, "the host's page told its room");

            right.focus().join();
            Thread.sleep(200);
            docking.moved(DockingTest.over(right, host, WindowMove.Phase.MOVING, 1060, 300, false));
            docking.moved(DockingTest.over(right, host, WindowMove.Phase.ENDED, 1060, 300, false));
            assertEquals(1380, until(() -> host.geometry().join().width(), w -> Math.abs(w - 1380) < 2), 2, "and again: + 300 (its own, within 35%)");
            assertEquals(220, host.geometry().join().x(), 2, "to the right: the window stays where it was");
            assertEquals(300, until(() -> cssVar(host, "right"), v -> Math.abs(v - 300) < 1), 1);
            assertEquals(280, DockTest.innerWidth(left), 3, "the left one kept its size");

            // the left one torn off: the host shrinks back, its room closes up
            left.detach("swaswa test: left again", 1500, 100, 400, 300).join();
            assertEquals(1100, until(() -> host.geometry().join().width(), w -> Math.abs(w - 1100) < 2), 2, "back by 280");
            assertEquals(0, until(() -> cssVar(host, "left"), v -> v < 1), 1, "no room at the left any more");
            assertEquals(300, cssVar(host, "right"), 1, "the right one's still");
            docking.close();
        }
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void withoutGrowingThePanelTakesItsRoomInside() throws Exception {
        try (App app = App.start()) {
            View host = app.open(WindowOptions.html("<p>h</p>").title("swaswa test: fixed host").size(800, 600).position(500, 100)).join();
            View panel = DockTest.stateful(app, "swaswa test: inside panel", 1500);
            panel.focus().join();
            Docking docking = Docking.on(app).grow(false);
            docking.moved(DockingTest.over(panel, host, WindowMove.Phase.MOVING, 760, 300, false));
            docking.moved(DockingTest.over(panel, host, WindowMove.Phase.ENDED, 760, 300, false));
            assertEquals(280, until(() -> DockTest.innerWidth(panel), w -> Math.abs(w - 280) < 3), 3, "the panel at the right");
            assertEquals(800, host.geometry().join().width(), 2, "the host as it was");
            assertEquals(500, host.geometry().join().x(), 2);
            docking.close();
        }
    }
}
