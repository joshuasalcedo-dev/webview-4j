package io.joshuasalcedo.swaswa.ffi;

import static io.joshuasalcedo.swaswa.ffi.ViewMethodsTest.until;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.foreign.MemorySegment;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * With the real mouse (-Dswaswa.mouse=true): MDI children moved and raised by a person inside their window, and a tab
 * torn off into a window of its own, then docked back by dragging it over its window, its page running throughout.
 */
@EnabledOnOs(OS.WINDOWS)
@EnabledIfSystemProperty(named = "swaswa.mouse", matches = "true")
@Timeout(120)
class DockMouseTest {

    /** An MDI child's page: a title bar of its own that moves it, and a count of the clicks it got. */
    static final String CHILD = """
            <body style="margin:0;font:13px system-ui;background:#fff;height:100vh;box-sizing:border-box;border:1px solid #889">
            <div id=bar style="height:28px;background:#3157d5;color:#fff;line-height:28px;padding:0 8px">a child</div>
            <script>
              window.__downs = 0;
              addEventListener('pointerdown', () => window.__downs++);
              document.getElementById('bar').addEventListener('mousedown', (e) => { if (e.button === 0) __webview4j.window.startDrag(); });
            </script>""";

    static int downs(View v) {
        return ((Number) v.eval("window.__downs").join()).intValue();
    }

    /** A point inside the host window's page area, in screen pixels: its client origin plus (x, y) logical. */
    static int[] inHost(MemorySegment host, double scale, double x, double y) {
        int[] r = Win32.rect(host);
        int frame = Win32.frameHeight(host);
        return new int[] {r[0] + 8 + (int) Math.round(x * scale), r[1] + frame + (int) Math.round(y * scale)};
    }

    @Test
    void mdiChildrenAreMovedAndRaisedByAPerson() throws Exception {
        try (App app = App.start()) {
            BlockingQueue<WindowMove> moves = new LinkedBlockingQueue<>();
            app.subscribe(WindowMove.class, moves::add);
            View host = app.open(WindowOptions.html("<body style='background:#dde'>").title("swaswa test: mdi").size(900, 650).position(100, 80)).join();
            View one = host.embed(WindowOptions.html(CHILD).origins("null"), 40, 60, 320, 220).join();
            View two = host.embed(WindowOptions.html(CHILD).origins("null"), 200, 140, 320, 220).join();
            for (View v : new View[] {one, two}) {
                until(() -> v.eval("typeof window.__downs").join(), "number"::equals);
                v.setFloating(true).join();
            }
            MemorySegment h = Win32.window("swaswa test: mdi");
            double scale = host.geometry().join().scaleFactor();
            ViewMethodsTest.front(host);
            Thread.sleep(400);

            // where they overlap: the one made last is on top; raised, the other is
            int[] overlap = inHost(h, scale, 260, 220);
            two.toFront().join();
            Win32.drag(overlap[0], overlap[1], overlap[0], overlap[1]);
            assertEquals(1, until(() -> downs(two), n -> n > 0), "the top one got the click");
            int oneBefore = downs(one);
            one.toFront().join();
            Win32.drag(overlap[0], overlap[1], overlap[0], overlap[1]);
            assertEquals(oneBefore + 1, until(() -> downs(one), n -> n > oneBefore), "raised: now it gets it");

            // moved by its own title bar, inside its window, five times: the child stays under the pointer where it
            // was grabbed (the same offset when the move begins and when it ends), and its moves carry its own id
            int[] grabAt = {40 + 100, 60 + 14};
            for (int round = 0; round < 5; round++) {
                moves.clear();
                int dx = round % 2 == 0 ? 160 : -160, dy = round % 2 == 0 ? 90 : -90;
                int[] bar = inHost(h, scale, grabAt[0], grabAt[1]);
                Win32.drag(bar[0], bar[1], bar[0] + dx, bar[1] + dy);
                WindowMove started = moves.poll(10, TimeUnit.SECONDS), ended = PointerTest.endOf(moves);
                assertNotNull(started, "round " + round + ": its move began");
                assertEquals(one.id(), started.view());
                assertEquals(WindowMove.Phase.STARTED, started.phase());
                assertNotNull(ended, "round " + round + ": and ended");
                // where it was grabbed: the press (the move takes the pointer's place as the page asks for it; the
                // STARTED report reads the pointer a moment later, a step on)
                int grabbedX = bar[0] - started.x(), grabbedY = bar[1] - started.y();
                assertEquals(grabbedX, ended.cursorX() - ended.x(), 3, "round " + round + ": held where grabbed (x): " + started + " -> " + ended);
                assertEquals(grabbedY, ended.cursorY() - ended.y(), 3, "round " + round + ": held where grabbed (y)");
                assertTrue(Math.abs(ended.x() - started.x()) > 100, "round " + round + ": it went with the pointer: " + (ended.x() - started.x()));
                grabAt[0] += (int) Math.round((ended.x() - started.x()) / scale);
                grabAt[1] += (int) Math.round((ended.y() - started.y()) / scale);
            }
        }
    }

    @Test
    void aTabTornOffAndDockedBackKeepsItsPage() throws Exception {
        try (App app = App.start()) {
            BlockingQueue<WindowMove> moves = new LinkedBlockingQueue<>();
            BlockingQueue<ViewReparented> reparented = new LinkedBlockingQueue<>();
            app.subscribe(WindowMove.class, moves::add);
            app.subscribe(ViewReparented.class, reparented::add);
            View host = app.open(WindowOptions.html("<body style='background:#dde'>").title("swaswa test: dock host").size(900, 600).position(100, 80)).join();
            View tab = host.embed(WindowOptions.html("""
                    <body style="margin:0;font:13px system-ui">
                    <div id=tab style="height:32px;width:160px;background:#3157d5;color:#fff;line-height:32px;padding:0 10px">a tab: pull me out</div>
                    <input id=typed>
                    <script>
                      window.__count = 0;
                      document.getElementById('tab').addEventListener('mousedown', (e) => { if (e.button === 0) __webview4j.invoke('tear-off', {}); });
                    </script>""").origins("null"), 0, 0, 900, 600).join();   // its own page: the bridge answers it
            until(() -> tab.eval("typeof window.__count").join(), "number"::equals);
            tab.eval("window.__count = 7; document.getElementById('typed').value = 'kept'").join();

            // the page asks; Java tears it off: a window that follows the mouse while the button is held
            app.handle("tear-off", call -> tab.tearOff("swaswa test: torn tab", 520, 380).join());
            // let go of a dragged window over the host: docked back in
            app.subscribe(WindowMove.class, move -> {
                if (move.phase() == WindowMove.Phase.ENDED && move.view().equals(tab.id())) {
                    Optional<View> under = app.windowUnderCursor(tab).join();
                    if (under.equals(Optional.of(host))) host.adopt(tab, 0, 0, 900, 600);
                }
            });

            MemorySegment h = Win32.window("swaswa test: dock host");
            double scale = host.geometry().join().scaleFactor();
            ViewMethodsTest.front(host);
            Thread.sleep(400);
            int[] grab = inHost(h, scale, 60, 16);
            int[] hostRect = Win32.rect(h);
            int[] away = {hostRect[2] + 300, hostRect[1] + 200};   // beside the host, outside it
            Win32.drag(grab[0], grab[1], away[0], away[1]);

            assertEquals(new ViewReparented(tab.id(), null), reparented.poll(10, TimeUnit.SECONDS), "torn off into a window");
            MemorySegment torn = Win32.window("swaswa test: torn tab");
            assertNotEquals(0, torn.address());
            int[] tornRect = until(() -> Win32.rect(torn), r -> r[0] > hostRect[2]);
            assertTrue(tornRect[0] > hostRect[2], "it went with the mouse, beside the host: " + java.util.Arrays.toString(tornRect));
            assertEquals(7, ((Number) tab.eval("window.__count").join()).intValue(), "its page, running on");

            // its title bar dragged back over the host: docked in again
            Thread.sleep(400);
            int[] title = {tornRect[0] + 80, tornRect[1] + 12};
            int[] intoHost = {(hostRect[0] + hostRect[2]) / 2, (hostRect[1] + hostRect[3]) / 2};
            Win32.drag(title[0], title[1], intoHost[0], intoHost[1]);
            assertEquals(new ViewReparented(tab.id(), host.id()), reparented.poll(10, TimeUnit.SECONDS), "docked back into the host");
            assertEquals(7, ((Number) tab.eval("window.__count").join()).intValue(), "the same page the whole way");
            assertEquals("kept", tab.eval("document.getElementById('typed').value").join());
            assertEquals(0, Win32.window("swaswa test: torn tab", 300).address(), "its window is gone");
        }
    }
}
