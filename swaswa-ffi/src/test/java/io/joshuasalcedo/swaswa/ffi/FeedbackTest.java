package io.joshuasalcedo.swaswa.ffi;

import static io.joshuasalcedo.swaswa.ffi.ViewMethodsTest.until;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.foreign.MemorySegment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * What a person sees of what they can do: a hand over what can be dragged, the window's state on its page (active,
 * floating, being dragged, a dock target) for its own styles, and words saying what letting go of a dragged window
 * does.
 */
@EnabledOnOs(OS.WINDOWS)
@Timeout(120)
class FeedbackTest {

    static boolean has(View view, String attribute) {
        return Boolean.TRUE.equals(view.eval("document.documentElement.hasAttribute('data-swaswa-" + attribute + "')").join());
    }

    static Object attribute(View view, String attribute) {
        return view.eval("document.documentElement.getAttribute('data-swaswa-" + attribute + "')").join();
    }

    static View page(App app, WindowOptions options) throws InterruptedException {
        View view = app.open(options).join();
        until(() -> view.eval("document.readyState").join(), "complete"::equals);
        return view;
    }

    @Test
    void whatCanBeDraggedShowsAHand() throws Exception {
        try (App app = App.start()) {
            View view = page(app, WindowOptions.html("""
                    <div id=bar data-webview4j-drag-region style="height:30px">a title bar</div>
                    <div id=other data-webview4j-drag-region style="height:30px;--swaswa-drag-cursor:move">its own cursor</div>
                    <p id=plain>not draggable</p>""").headless(true));
            assertEquals("grab", until(() -> view.eval("getComputedStyle(document.getElementById('bar')).cursor").join(), "grab"::equals), "a drag region: a hand");
            assertEquals("move", view.eval("getComputedStyle(document.getElementById('other')).cursor").join(), "the page's own choice");
            assertEquals("auto", view.eval("getComputedStyle(document.getElementById('plain')).cursor").join(), "the rest as it is");

            view.eval("document.getElementById('bar').dispatchEvent(new MouseEvent('mousedown', { bubbles: true, button: 0 }))").join();
            assertTrue(has(view, "holding"), "held: said on the page");
            assertEquals("grabbing", view.eval("getComputedStyle(document.getElementById('bar')).cursor").join(), "a closed hand while held");
            view.eval("dispatchEvent(new MouseEvent('mouseup', { bubbles: true }))").join();
            assertFalse(has(view, "holding"));
        }
    }

    @Test
    void theActiveWindowAndADraggedOneSaySoOnTheirPages() throws Exception {
        try (App app = App.start()) {
            View a = page(app, WindowOptions.html("<p>a</p>").title("swaswa test: state a").size(300, 200).position(100, 100));
            View b = page(app, WindowOptions.html("<p>b</p>").title("swaswa test: state b").size(300, 200).position(500, 100));
            ViewMethodsTest.front(a);
            assertTrue(until(() -> has(a, "active"), on -> on), "the focused one: active");
            assertFalse(has(b, "active"));
            ViewMethodsTest.front(b);
            assertTrue(until(() -> has(b, "active"), on -> on));
            assertFalse(until(() -> has(a, "active"), on -> !on), "the other no longer");

            MemorySegment hb = Win32.window("swaswa test: state b");
            Win32.moving(hb, true);
            assertTrue(until(() -> has(b, "dragging"), on -> on), "being moved: dragging");
            Win32.moving(hb, false);
            assertFalse(until(() -> has(b, "dragging"), on -> !on), "let go: no longer");
        }
    }

    @Test
    void mdiChildrenSayWhichIsActive() throws Exception {
        try (App app = App.start()) {
            View host = page(app, WindowOptions.html("<p>host</p>").headless(true).size(800, 600));
            View one = host.embed(WindowOptions.html("<p>one</p>"), 20, 20, 300, 200).join();
            View two = host.embed(WindowOptions.html("<p>two</p>"), 200, 100, 300, 200).join();
            for (View v : new View[] {one, two}) {
                until(() -> v.eval("document.readyState").join(), "complete"::equals);
                v.setFloating(true).join();
            }
            assertTrue(until(() -> has(one, "floating"), on -> on), "an MDI child: floating");
            one.toFront().join();
            assertTrue(until(() -> has(one, "active"), on -> on), "raised: active");
            assertFalse(has(two, "active"));
            two.toFront().join();
            assertTrue(until(() -> has(two, "active"), on -> on));
            assertFalse(until(() -> has(one, "active"), on -> !on), "the other, no longer");
        }
    }

    static View overlayOf(App app, View... not) {
        java.util.Set<View> others = java.util.Set.of(not);
        return app.views().join().stream().map(v -> app.view(v.id())).filter(v -> !others.contains(v)).findFirst().orElseThrow();
    }

    @Test
    void aDraggedWindowIsToldWhatLettingGoDoes() throws Exception {
        try (App app = App.start()) {
            View host = page(app, WindowOptions.html("<p>host</p>").title("swaswa test: hinted").size(800, 600).position(100, 80));
            View panel = DockTest.stateful(app, "swaswa test: hinted panel", 1000);
            panel.focus().join();
            Thread.sleep(300);
            Docking docking = Docking.on(app);

            docking.moved(DockingTest.over(panel, host, WindowMove.Phase.MOVING, 40, 300, false));
            View overlay = overlayOf(app, host, panel);
            assertEquals("Release to dock on the left · hold Ctrl to keep it floating",
                    until(() -> overlay.eval("document.querySelector('.hint')?.textContent ?? null").join(), java.util.Objects::nonNull));
            assertEquals("left", until(() -> attribute(host, "dock-target"), "left"::equals), "the host's page knows where");

            docking.moved(DockingTest.over(panel, host, WindowMove.Phase.MOVING, 40, 300, true));
            assertEquals("Kept floating · let go of Ctrl to dock",
                    until(() -> overlay.eval("document.querySelector('.hint')?.textContent ?? null").join(), "Kept floating · let go of Ctrl to dock"::equals));
            assertFalse(until(() -> has(host, "dock-target"), on -> !on), "nowhere to go with Ctrl: no target");

            docking.moved(DockingTest.over(panel, host, WindowMove.Phase.ENDED, 40, 300, true));
            assertFalse(has(host, "dock-target"), "the drag over: gone");
            docking.close();

            // words of the app's own, or none
            Docking own = Docking.on(app).hintText((zone, held) -> zone == null ? null : "Put it " + zone.name().toLowerCase());
            own.moved(DockingTest.over(panel, host, WindowMove.Phase.MOVING, 400, 300, false));
            View overlay2 = overlayOf(app, host, panel);
            assertEquals("Put it center", until(() -> overlay2.eval("document.querySelector('.hint')?.textContent ?? null").join(), java.util.Objects::nonNull));
            own.close();
            Docking quiet = Docking.on(app).hints(false);
            quiet.moved(DockingTest.over(panel, host, WindowMove.Phase.MOVING, 400, 300, false));
            View overlay3 = overlayOf(app, host, panel);
            until(() -> overlay3.eval("document.querySelectorAll('.area').length").join(), n -> ((Number) n).intValue() == 1);
            assertEquals(0, ((Number) overlay3.eval("document.querySelectorAll('.hint').length").join()).intValue(), "no hint");
            quiet.close();
        }
    }

    @Test
    void aWindowsFrameTakesAColour() throws Exception {
        try (App app = App.start()) {
            View view = page(app, WindowOptions.html("<body style='background:#fff'>").title("swaswa test: border").size(300, 200).position(200, 200));
            MemorySegment hwnd = Win32.window("swaswa test: border");
            ViewMethodsTest.front(view);   // in front: its frame is what the screen shows there
            Thread.sleep(300);
            try {
                view.setBorderColor(0x31, 0x57, 0xd5).join();
            } catch (java.util.concurrent.CompletionException e) {
                org.junit.jupiter.api.Assumptions.assumeFalse(e.getCause() instanceof NotSupportedException, "this Windows has no border colours");
                throw e;
            }
            long lit = until(() -> Win32.frameColorOnScreen(hwnd), c -> Win32.near(c, 0xd55731L));
            assertTrue(Win32.near(lit, 0xd55731L), "its frame, on the screen, in that colour: 0x" + Long.toHexString(lit));
            view.clearBorderColor().join();
            long plain = until(() -> Win32.frameColorOnScreen(hwnd), c -> !Win32.near(c, 0xd55731L));
            assertFalse(Win32.near(plain, 0xd55731L), "the system's own again: 0x" + Long.toHexString(plain));
            assertThrows(IllegalArgumentException.class, () -> view.setBorderColor(300, 0, 0));
        }
    }

    @Test
    void theWindowDraggedOverIsLitAsAWhole() throws Exception {
        try (App app = App.start()) {
            View host = page(app, WindowOptions.html("<body style='background:#fff'>").title("swaswa test: lit host").size(800, 600).position(100, 80));
            View panel = DockTest.stateful(app, "swaswa test: lit panel", 1000);
            ViewMethodsTest.front(host);   // its left edge in view (the panel is far to the right)
            Thread.sleep(300);
            MemorySegment hh = Win32.window("swaswa test: lit host");
            long before = Win32.frameColorOnScreen(hh);
            Docking docking = Docking.on(app).frameColor(0x10, 0x80, 0x40);

            docking.moved(DockingTest.over(panel, host, WindowMove.Phase.MOVING, 400, 300, false));
            View overlay = overlayOf(app, host, panel);
            assertEquals(1, ((Number) until(() -> overlay.eval("document.querySelectorAll('.host').length").join(), n -> ((Number) n).intValue() == 1)).intValue(),
                    "a rim around all of it");
            assertTrue(Win32.near(until(() -> Win32.frameColorOnScreen(hh), c -> Win32.near(c, 0x408010L)), 0x408010L), "its frame lit (Windows 11)");
            docking.moved(DockingTest.over(panel, host, WindowMove.Phase.ENDED, 400, 300, true));   // let go with Ctrl: nothing docked
            assertTrue(Win32.near(until(() -> Win32.frameColorOnScreen(hh), c -> Win32.near(c, before)), before), "its frame as it was");
            docking.close();

            Docking plain = Docking.on(app).hostHighlight(false);
            plain.moved(DockingTest.over(panel, host, WindowMove.Phase.MOVING, 400, 300, false));
            View overlay2 = overlayOf(app, host, panel);
            until(() -> overlay2.eval("document.querySelectorAll('.area').length").join(), n -> ((Number) n).intValue() == 1);
            assertEquals(0, ((Number) overlay2.eval("document.querySelectorAll('.host').length").join()).intValue(), "off: no rim");
            Thread.sleep(300);
            assertTrue(Win32.near(Win32.frameColorOnScreen(hh), before), "and its frame left alone");
            plain.close();
        }
    }
}
