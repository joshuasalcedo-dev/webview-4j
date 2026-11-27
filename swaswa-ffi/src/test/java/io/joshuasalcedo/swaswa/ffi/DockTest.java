package io.joshuasalcedo.swaswa.ffi;

import static io.joshuasalcedo.swaswa.ffi.ExceptionsTest.failure;
import static io.joshuasalcedo.swaswa.ffi.ViewMethodsTest.until;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * Views moving between windows with their pages running: a window's view docked into another window, taken out again
 * into a window of its own, an embedded view torn off, and embedded views that float (MDI children). Nothing reloads:
 * a page's state is the proof.
 */
@EnabledOnOs(OS.WINDOWS)
@Timeout(120)
class DockTest {

    static final String STATEFUL = "<body style='margin:0'><input id=typed><script>window.__count = 0;</script>";

    static View stateful(App app, String title, double x) throws InterruptedException {
        View view = app.open(WindowOptions.html(STATEFUL).title(title).size(400, 300).position(x, 100)).join();
        until(() -> view.eval("typeof window.__count === 'number'").join(), Boolean.TRUE::equals);
        view.eval("window.__count = 5; document.getElementById('typed').value = 'kept'").join();
        return view;
    }

    static void stillTheSamePage(View view) {
        assertEquals(5, ((Number) view.eval("window.__count").join()).intValue(), "its page's state, kept");
        assertEquals("kept", view.eval("document.getElementById('typed').value").join(), "and what was typed in it");
    }

    /**
     * The page's width in the screen's pixels (its CSS width times its pixel ratio: the system's text size, e.g.
     * 110%, makes a page's CSS pixels larger than the screen's), on a monitor at 100% the logical width.
     */
    static double innerWidth(View view) {
        return ((Number) view.eval("Math.round(innerWidth * devicePixelRatio)").join()).doubleValue();
    }

    @Test
    void aWindowsViewDocksIntoAnotherAndComesOutAgain() throws Exception {
        try (App app = App.start()) {
            BlockingQueue<ViewReparented> moved = new LinkedBlockingQueue<>();
            List<WebViewEvent> seen = new CopyOnWriteArrayList<>();
            app.subscribe(ViewReparented.class, moved::add);
            View panel = stateful(app, "swaswa test: panel", 100);
            app.subscribe(WebViewEvent.class, e -> { if ((e instanceof Closed || e instanceof PageLoad) && panel.id().equals(e.view())) seen.add(e); });
            View host = app.open(WindowOptions.html("<body style='background:#eee'>host").title("swaswa test: host").size(800, 600).position(560, 100)).join();
            seen.clear();

            host.adopt(panel, 20, 30, 300, 200).join();
            assertEquals(new ViewReparented(panel.id(), host.id()), moved.poll(10, TimeUnit.SECONDS));
            stillTheSamePage(panel);
            assertEquals(300, until(() -> innerWidth(panel), w -> Math.abs(w - 300) < 3), 3, "the size it was given in its host");
            assertEquals(0, Win32.window("swaswa test: panel", 300).address(), "its own window is gone");
            assertTrue(seen.isEmpty(), "no Closed, no reload: " + seen);
            assertTrue(app.views().join().stream().anyMatch(v -> v.id().equals(panel.id())), "still a view of the app");
            failure(NotSupportedException.class, panel.setTitle("x"));   // embedded now: no window of its own

            panel.place(40, 40, 420, 260).join();
            assertEquals(420, until(() -> innerWidth(panel), w -> Math.abs(w - 420) < 3), 3, "place moves and sizes it in its host");

            panel.detach("swaswa test: panel again", 150, 150, 500, 380).join();
            assertEquals(new ViewReparented(panel.id(), null), moved.poll(10, TimeUnit.SECONDS));
            stillTheSamePage(panel);
            assertNotEquals(0, Win32.window("swaswa test: panel again").address(), "a window of its own again");
            assertEquals(500, panel.geometry().join().width(), 2);
            panel.setSize(640, 400).join();
            assertEquals(640, until(() -> innerWidth(panel), w -> Math.abs(w - 640) < 3), 3, "its page follows its new window's size");
            assertTrue(seen.isEmpty(), "and still nothing reloaded or closed: " + seen);
        }
    }

    @Test
    void anEmbeddedViewTornOffIsAWindowThatSizesItsPage() throws Exception {
        try (App app = App.start()) {
            View host = app.open(WindowOptions.html("<p>host</p>").title("swaswa test: tabs").size(800, 600).position(100, 100)).join();
            View tab = host.embed(WindowOptions.html(STATEFUL), 0, 40, 800, 560).join();
            until(() -> tab.eval("typeof window.__count === 'number'").join(), Boolean.TRUE::equals);
            tab.eval("window.__count = 5; document.getElementById('typed').value = 'kept'").join();

            tab.detach("swaswa test: torn off", 300, 200, 520, 360).join();
            stillTheSamePage(tab);
            assertEquals(520, until(() -> innerWidth(tab), w -> Math.abs(w - 520) < 3), 3, "it fills its window");
            tab.setSize(700, 500).join();
            assertEquals(700, until(() -> innerWidth(tab), w -> Math.abs(w - 700) < 3), 3, "and follows its size");

            // and back in
            host.adopt(tab, 0, 40, 800, 560).join();
            stillTheSamePage(tab);
            assertEquals(800, until(() -> innerWidth(tab), w -> Math.abs(w - 800) < 3), 3, "docked back");
        }
    }

    @Test
    void closingTheHostClosesWhatItTookIn() throws Exception {
        try (App app = App.start()) {
            BlockingQueue<Closed> closed = new LinkedBlockingQueue<>();
            app.subscribe(Closed.class, closed::add);
            View panel = stateful(app, "swaswa test: docked", 100);
            View host = app.open(WindowOptions.html("<p>h</p>").title("swaswa test: closing host").size(600, 400).position(560, 100)).join();
            host.adopt(panel, 0, 0, 300, 200).join();
            host.close().join();
            List<String> gone = List.of(closed.poll(10, TimeUnit.SECONDS).view(), closed.poll(10, TimeUnit.SECONDS).view());
            assertTrue(gone.containsAll(List.of(panel.id(), host.id())), "both: " + gone);
        }
    }

    @Test
    void whatCannotMoveSaysWhy() throws Exception {
        try (App app = App.start()) {
            View a = app.open(WindowOptions.html("<p>a</p>").headless(true)).join();
            View b = app.open(WindowOptions.html("<p>b</p>").headless(true)).join();
            View in = b.embed(WindowOptions.html("<p>in</p>"), 0, 0, 50, 50).join();
            assertTrue(failure(InvalidRequestException.class, a.adopt(a, 0, 0, 10, 10)).hint() != null, "itself");
            assertTrue(failure(NotSupportedException.class, a.adopt(in, 0, 0, 10, 10)).hint().contains("place("), "an embedded one: place or detach");
            assertTrue(failure(NotSupportedException.class, in.adopt(a, 0, 0, 10, 10)).hint().contains("window's own view"), "into an embedded one");
            assertTrue(failure(NotSupportedException.class, a.detach("x", 0, 0, 100, 100)).hint().contains("embedded"), "a window detached");
            assertTrue(failure(NotSupportedException.class, a.toFront()).hint().contains("focus()"), "a window to the front: focus()");
        }
    }

    @Test
    void aFloatingViewStaysFloatingAcrossItsPagesLoads() throws Exception {
        try (App app = App.start()) {
            View host = app.open(WindowOptions.html("<p>mdi</p>").headless(true).size(800, 600)).join();
            View child = host.embed(WindowOptions.html("<p>child</p>"), 40, 40, 300, 200).join();
            child.setFloating(true).join();
            assertEquals(true, until(() -> child.eval("window.__webview4j_floating === true").join(), Boolean.TRUE::equals));
            child.toFront().join();
            child.reload().join();
            assertEquals(true, until(() -> child.eval("window.__webview4j_floating === true").join(), Boolean.TRUE::equals), "after a reload too");
            child.setFloating(false).join();
            assertEquals(false, child.eval("window.__webview4j_floating === true").join());
        }
    }
}
