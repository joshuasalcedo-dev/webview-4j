package io.joshuasalcedo.swaswa.ffi;

import static io.joshuasalcedo.swaswa.ffi.EguiTest.center;
import static io.joshuasalcedo.swaswa.ffi.EguiTest.click;
import static io.joshuasalcedo.swaswa.ffi.EguiTest.widget;
import static io.joshuasalcedo.swaswa.ffi.ViewMethodsTest.until;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.foreign.MemorySegment;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** egui windows without the system's frame: egui's own title bar and its buttons, a drag region of the app's own design. */
@EnabledOnOs(OS.WINDOWS)
@Timeout(120)
class EguiTitleBarTest {

    static Map<?, ?> state(ImmediateWindow w) {
        return ((Egui.Window) w).state().join();
    }

    @Test
    void framelessItHasEguisTitleBarAndItsButtonsWork() throws Exception {
        try (App app = App.start()) {
            ImmediateWindow window = Egui.on(app).open(Immediate.Options.of("swaswa test: frameless egui", 420, 300).frameless(), ui -> {
                ui.titleBar("My app");
                ui.label("no system frame");
            });
            assertNotNull(widget(window, "My app"), "egui's own title bar");
            MemorySegment hwnd = Win32.window("swaswa test: frameless egui");
            assertTrue(Win32.frameHeight(hwnd) < 10, "no system title bar: " + Win32.frameHeight(hwnd));
            assertEquals(false, state(window).get("decorated"));

            double[] max = center(widget(window, "☐"));
            click(window, max[0], max[1]);
            assertEquals(true, until(() -> state(window).get("maximized"), Boolean.TRUE::equals), "maximized by its button");
            double[] restore = center(widget(window, "☐"));
            click(window, restore[0], restore[1]);
            assertEquals(false, until(() -> state(window).get("maximized"), Boolean.FALSE::equals), "and restored");

            double[] min = center(widget(window, "—"));
            click(window, min[0], min[1]);
            assertEquals(true, until(() -> state(window).get("minimized"), Boolean.TRUE::equals), "minimized by its button");
            window.close();
        }
    }

    @Test
    void itsCloseButtonClosesIt() throws Exception {
        try (App app = App.start()) {
            ImmediateWindow window = Egui.on(app).open(Immediate.Options.of("swaswa test: closing egui", 420, 300).frameless(), ui -> ui.titleBar("Close me"));
            double[] close = center(widget(window, "✕"));
            click(window, close[0], close[1]);
            window.whenClosed().get(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void aDragRegionOfTheAppsOwnKeepsItsButtons() throws Exception {
        try (App app = App.start()) {
            AtomicInteger pressed = new AtomicInteger();
            ImmediateWindow window = Egui.on(app).open(Immediate.Options.of("swaswa test: own bar", 420, 300).frameless(), ui -> {
                ui.dragRegion(40, bar -> {
                    bar.heading("Brand");
                    if (bar.button("Menu")) pressed.incrementAndGet();
                    if (bar.button("Quit")) bar.window().close();
                });
                ui.label("below the bar");
            });
            Map<String, Object> region = until(() -> EguiTest.drawn(window).stream().filter(w -> "drag-region".equals(w.get("kind"))).findFirst().orElse(null),
                    java.util.Objects::nonNull);
            assertEquals(40, ((Number) region.get("height")).doubleValue(), 0.5, "the band, its height");
            double[] menu = center(widget(window, "Menu"));
            click(window, menu[0], menu[1]);
            assertEquals(1, (int) until(pressed::get, n -> n == 1), "a button in the band is the button's");
            double[] quit = center(widget(window, "Quit"));
            click(window, quit[0], quit[1]);
            window.whenClosed().get(10, TimeUnit.SECONDS);   // ui.window().close()
        }
    }

    @Test
    void theFrameComesAndGoes() throws Exception {
        try (App app = App.start()) {
            ImmediateWindow window = Egui.on(app).open("swaswa test: toggled frame", 420, 300, ui -> ui.label("frame"));
            widget(window, "frame");
            MemorySegment hwnd = Win32.window("swaswa test: toggled frame");
            assertTrue(Win32.frameHeight(hwnd) > 20, "the system's title bar");
            window.setDecorations(false);
            assertTrue(until(() -> Win32.frameHeight(hwnd), h -> h < 10) < 10, "gone");
            window.setDecorations(true);
            assertTrue(until(() -> Win32.frameHeight(hwnd), h -> h > 20) > 20, "back");
            window.close();
        }
    }
}
