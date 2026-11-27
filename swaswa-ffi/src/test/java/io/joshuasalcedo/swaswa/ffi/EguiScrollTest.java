package io.joshuasalcedo.swaswa.ffi;

import static io.joshuasalcedo.swaswa.ffi.EguiTest.drawn;
import static io.joshuasalcedo.swaswa.ffi.EguiTest.widget;
import static io.joshuasalcedo.swaswa.ffi.ViewMethodsTest.until;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * Scrolling in egui, driven from Java: a log of a hundred thousand lines of which only those in view are drawn, held
 * at its end as it grows, let go when the person scrolls up, held again back at the end.
 */
@EnabledOnOs(OS.WINDOWS)
@Timeout(120)
class EguiScrollTest {

    /** The monospace lines the last frame drew, by their number. */
    static List<Integer> linesShown(ImmediateWindow window) {
        return drawn(window).stream().filter(w -> "monospace".equals(w.get("kind")))
                .map(w -> Integer.parseInt(String.valueOf(w.get("label")).replace("line ", ""))).toList();
    }

    /** The lines shown once the view has come to rest (the same twice, a little apart). */
    static List<Integer> settled(ImmediateWindow window) throws InterruptedException {
        List<Integer> before = linesShown(window);
        for (int i = 0; i < 50; i++) {
            Thread.sleep(100);
            window.repaint();
            Thread.sleep(50);
            List<Integer> now = linesShown(window);
            if (!now.isEmpty() && now.equals(before)) return now;
            before = now;
        }
        return before;
    }

    static void wheel(ImmediateWindow window, double x, double y, double dy) {
        ((Egui.Window) window).input(List.of(Map.of("kind", "move", "x", x, "y", y), Map.of("kind", "wheel", "x", 0, "y", dy))).join();
    }

    @Test
    void aHundredThousandLinesDrawOnlyThoseInViewAndFollowTheEnd() throws Exception {
        try (App app = App.start()) {
            AtomicInteger count = new AtomicInteger(100_000);
            AtomicReference<int[]> given = new AtomicReference<>();
            AtomicLong nanos = new AtomicLong();
            ImmediateWindow window = Egui.on(app).open("swaswa test: log", 500, 400, ui -> {
                long t = System.nanoTime();
                ui.heading("log");
                ui.rows("log", count.get(), 0, true, (rows, from, to) -> {
                    given.set(new int[] {from, to});
                    for (int i = from; i < to; i++) rows.monospace("line " + i);
                });
                nanos.set(System.nanoTime() - t);
            });
            List<Integer> shown = until(() -> linesShown(window), l -> l.contains(99_999));
            assertTrue(shown.contains(99_999), "at the end: the last line shown");
            assertTrue(shown.size() < 100, "only those in view drawn: " + shown.size());
            assertEquals(shown.getFirst(), given.get()[0], "Java given just that range");
            assertEquals(100_000, given.get()[1]);
            System.out.println("egui: a frame of a 100000-line log, " + shown.size() + " lines in view, in "
                    + Math.round(nanos.get() / 1e4) / 100.0 + " ms of Java's");

            // it grows: still at its end (once the view has come to rest there)
            settled(window);
            count.set(120_000);
            window.repaint();
            List<Integer> grown = until(() -> linesShown(window), l -> l.contains(119_999));
            assertTrue(grown.contains(119_999), "followed to the new end; shown " + (grown.isEmpty() ? "nothing"
                    : grown.getFirst() + ".." + grown.getLast()) + " (before it grew: " + shown.getFirst() + ".." + shown.getLast() + ")");

            // scrolled up by hand: let go; it grows, the view stays
            Map<String, Object> area = widget(window, "log");
            double ax = ((Number) area.get("x")).doubleValue() + 50, ay = ((Number) area.get("y")).doubleValue() + 50;
            for (int i = 0; i < 4; i++) wheel(window, ax, ay, 400);   // up
            until(() -> linesShown(window), l -> !l.isEmpty() && !l.contains(119_999));
            List<Integer> scrolledTo = settled(window);   // egui scrolls smoothly: where it comes to rest
            assertFalse(scrolledTo.contains(119_999), "scrolled up: the end out of view");
            count.set(130_000);
            window.repaint();
            Thread.sleep(300);
            List<Integer> after = linesShown(window);
            assertFalse(after.contains(129_999), "let go: new lines do not pull it down");
            assertEquals(scrolledTo.getFirst(), after.getFirst(), 2, "it stayed where the person left it");

            // back at the end by hand: held again
            for (int i = 0; i < 60; i++) wheel(window, ax, ay, -100_000);   // down, far
            until(() -> linesShown(window), l -> l.contains(129_999));
            count.set(140_000);
            window.repaint();
            assertTrue(until(() -> linesShown(window), l -> l.contains(139_999)).contains(139_999), "back at the end: it follows again");
            window.close();
        }
    }

    @Test
    void aScrollAreaKeepsItsEndInView() throws Exception {
        try (App app = App.start()) {
            ImmediateWindow window = Egui.on(app).open("swaswa test: scroll", 400, 300, ui -> {
                ui.heading("notes");
                ui.scroll("notes", 180, true, notes -> {
                    for (int i = 0; i < 300; i++) notes.label("note " + i);
                });
            });
            Map<String, Object> area = widget(window, "notes");
            double top = ((Number) area.get("y")).doubleValue(), bottom = top + ((Number) area.get("height")).doubleValue();
            Map<String, Object> last = until(() -> drawn(window).stream().filter(w -> "note 299".equals(w.get("label"))).findFirst().orElse(null),
                    w -> w != null && ((Number) w.get("y")).doubleValue() < bottom && ((Number) w.get("y")).doubleValue() >= top - 30);
            assertNotNull(last, "its last note in view");
            Map<String, Object> first = drawn(window).stream().filter(w -> "note 0".equals(w.get("label"))).findFirst().orElseThrow();
            assertTrue(((Number) first.get("y")).doubleValue() < top, "its first scrolled out of view, above");
            window.close();
        }
    }
}
