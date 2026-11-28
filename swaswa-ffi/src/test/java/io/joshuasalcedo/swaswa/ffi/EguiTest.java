package io.joshuasalcedo.swaswa.ffi;

import static io.joshuasalcedo.swaswa.ffi.ViewMethodsTest.until;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * egui driven from Java: each widget drawn, and answering what is done to it (a click, typing, at the place it was
 * drawn, given as the person's input would be); frames only when something happens; a frame that fails taken in its
 * stride; closed by Java or by the person.
 */
@EnabledOnOs(OS.WINDOWS)
@Timeout(120)
class EguiTest {

    /** What the last frame drew: kind, label, x, y, width, height. */
    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> drawn(ImmediateWindow window) {
        return (List<Map<String, Object>>) ((Egui.Window) window).drawn().join().get("widgets");
    }

    static long frames(ImmediateWindow window) {
        return ((Number) ((Egui.Window) window).drawn().join().get("frames")).longValue();
    }

    static Map<String, Object> widget(ImmediateWindow window, String label) throws InterruptedException {
        return until(() -> drawn(window).stream().filter(w -> label.equals(w.get("label"))).findFirst().orElse(null), java.util.Objects::nonNull);
    }

    static double[] center(Map<String, Object> w) {
        double x = ((Number) w.get("x")).doubleValue(), y = ((Number) w.get("y")).doubleValue();
        return new double[] {x + ((Number) w.get("width")).doubleValue() / 2, y + ((Number) w.get("height")).doubleValue() / 2};
    }

    /** A click as a hand makes it: the press, then (a frame later) the release. */
    static void click(ImmediateWindow window, double x, double y) throws InterruptedException {
        Egui.Window w = (Egui.Window) window;
        w.input(List.of(Map.of("kind", "down", "x", x, "y", y))).join();
        Thread.sleep(60);
        w.input(List.of(Map.of("kind", "up", "x", x, "y", y))).join();
        Thread.sleep(60);
    }

    @Test
    void eachWidgetIsDrawnAndAnswers() throws Exception {
        try (App app = App.start()) {
            AtomicInteger saves = new AtomicInteger();
            boolean[] on = {false};
            double[] level = {20};
            AtomicReference<String> name = new AtomicReference<>("");
            ImmediateWindow window = Egui.on(app).open("swaswa test: egui", 420, 320, ui -> {
                ui.heading("Settings");
                ui.label("saved " + saves.get() + " times");
                if (ui.button("Save")) saves.incrementAndGet();
                on[0] = ui.checkbox("Enabled", on[0]);
                level[0] = ui.slider("Level", level[0], 0, 100);
                name.set(ui.textField("name", name.get()));
                ui.separator();
                ui.horizontal(row -> {
                    row.button("Left");
                    row.button("Right");
                });
            });
            assertNotEquals(0, Win32.window("swaswa test: egui").address(), "a window of its own");
            for (String label : List.of("Settings", "saved 0 times", "Save", "Enabled", "Level", "name", "Left", "Right")) {
                assertNotNull(widget(window, label), "drawn: " + label);
            }
            assertEquals("heading", widget(window, "Settings").get("kind"));

            // a button: clicked where it was drawn, the frame's code hears it
            double[] save = center(widget(window, "Save"));
            click(window, save[0], save[1]);
            assertEquals(1, (int) until(saves::get, n -> n == 1), "the click reached Java");
            assertNotNull(widget(window, "saved 1 times"), "and the UI drawn from the new state");

            // a checkbox, a slider at its right end, a text field typed into
            double[] box = center(widget(window, "Enabled"));
            click(window, box[0], box[1]);
            assertTrue(until(() -> on[0], v -> v), "the checkbox, checked");
            Map<String, Object> slider = widget(window, "Level");
            double sx = ((Number) slider.get("x")).doubleValue() + 4, sy = center(slider)[1];
            click(window, sx, sy);
            assertTrue(until(() -> level[0], v -> v < 10) < 10, "the slider, near its start: " + level[0]);
            double[] field = center(widget(window, "name"));
            click(window, field[0], field[1]);
            ((Egui.Window) window).input(List.of(Map.of("kind", "text", "text", "hello"))).join();
            assertEquals("hello", until(name::get, "hello"::equals), "typed into the field");

            // a row: left to right
            Map<String, Object> left = widget(window, "Left"), right = widget(window, "Right");
            assertEquals(((Number) left.get("y")).doubleValue(), ((Number) right.get("y")).doubleValue(), 0.5, "one row");
            assertTrue(((Number) right.get("x")).doubleValue() > ((Number) left.get("x")).doubleValue(), "left to right");
            window.close();
            window.whenClosed().get(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void framesComeOnlyWhenSomethingHappens() throws Exception {
        try (App app = App.start()) {
            AtomicLong drawnByJava = new AtomicLong();
            ImmediateWindow window = Egui.on(app).open("swaswa test: idle", 300, 200, ui -> {
                drawnByJava.incrementAndGet();
                ui.label("idle");
            });
            widget(window, "idle");
            Thread.sleep(800);   // settled
            long settled = frames(window);
            Thread.sleep(1000);
            // a frame drawn over and over would be ~60 a second; a few come from the system (focus, the pointer passing)
            assertTrue(frames(window) - settled <= 6, "idle: no frames (" + (frames(window) - settled) + " in a second)");
            window.repaint();
            assertTrue(until(() -> frames(window), n -> n > settled) > settled, "repaint: a frame");
            assertTrue(drawnByJava.get() >= frames(window) - 1, "each frame ran Java's code");
            window.close();
        }
    }

    @Test
    void aFrameThatFailsDoesNotTakeTheAppDown() throws Exception {
        try (App app = App.start()) {
            AtomicInteger frame = new AtomicInteger();
            ImmediateWindow window = Egui.on(app).open("swaswa test: failing", 300, 200, ui -> {
                ui.label("frame " + frame.incrementAndGet());
                if (frame.get() == 2) throw new IllegalStateException("the app's own bug");
            });
            widget(window, "frame 1");
            window.repaint();
            window.repaint();
            assertTrue(until(frame::get, n -> n >= 3) >= 3, "frames go on after one failed");
            assertNotNull(app.open(WindowOptions.html("<p>still here</p>").headless(true)).join(), "and the app too");
            window.close();
        }
    }

    @Test
    void closedByThePersonItSaysSo() throws Exception {
        try (App app = App.start()) {
            ImmediateWindow window = Egui.on(app).open("swaswa test: closed", 300, 200, ui -> ui.label("bye"));
            widget(window, "bye");
            Win32.close(Win32.window("swaswa test: closed"));   // its close button, as a person would
            window.whenClosed().get(10, TimeUnit.SECONDS);
            assertEquals(0L, (long) until(() -> Win32.window("swaswa test: closed", 0).address(), a -> a == 0), "gone");
        }
    }

    @Test
    void aFrameOfTwoThousandWidgetsIsQuick() throws Exception {
        try (App app = App.start()) {
            AtomicLong nanos = new AtomicLong();
            ImmediateWindow window = Egui.on(app).open("swaswa test: many", 600, 400, ui -> {
                long t = System.nanoTime();
                for (int i = 0; i < 1000; i++) {
                    ui.label("row " + i);
                    ui.button("b" + i);
                }
                nanos.set(System.nanoTime() - t);
            });
            widget(window, "b999");
            for (int i = 0; i < 5; i++) {   // warmed up
                window.repaint();
                Thread.sleep(100);
            }
            double ms = nanos.get() / 1e6;
            // the calls themselves, without egui's work: as many that do almost nothing (an empty space)
            AtomicLong bare = new AtomicLong();
            ImmediateWindow calls = Egui.on(app).open("swaswa test: calls", 300, 200, ui -> {
                long t = System.nanoTime();
                for (int i = 0; i < 2000; i++) ui.space(0);
                bare.set(System.nanoTime() - t);
            });
            for (int i = 0; i < 5; i++) {
                calls.repaint();
                Thread.sleep(100);
            }
            System.out.println("egui: 2000 calls into egui (an empty space each) in " + Math.round(bare.get() / 1e4) / 100.0 + " ms");
            assertTrue(bare.get() / 1e6 < 5, "the calls themselves: 2000 in a few ms at most: " + bare.get() / 1e6 + " ms");
            calls.close();
            System.out.println("egui: 2000 widgets drawn from Java in " + Math.round(ms * 10) / 10.0 + " ms");
            // egui's own layout of 2000 widgets is slow in a dev build (unoptimized): bounded loosely; what is ours,
            // the calls between Java and egui, is checked below
            assertTrue(ms < 400, "2000 widgets: " + ms + " ms");
            window.close();
        }
    }
}
