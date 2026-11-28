package io.joshuasalcedo.swaswa.ffi;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * A window's frame, buttons and taskbar, and its page's controls: what the native side was given reaches it, and what
 * the window and its page do comes back as events.
 */
class WindowFeaturesTest {

    @Test
    void shortcutsAreCheckedAndInTheirOrder() {
        assertEquals(List.of("Mod+S", "Mod+Shift+P", "F5", "Escape", "Alt+1"), View.shortcutsOf("Mod+S", "Mod+Shift+P", "F5", "Escape", "Alt+1"));
        assertThrows(IllegalArgumentException.class, () -> View.shortcutsOf("Shift+Mod+S"), "modifiers in their order");
        assertThrows(IllegalArgumentException.class, () -> View.shortcutsOf("Mod+"));
        assertThrows(IllegalArgumentException.class, () -> View.shortcutsOf("mod+s"));
    }

    @Test
    void theOptionsGoToTheNativeSide() {
        var payload = WindowOptions.html("<p>x</p>").maxSize(800, 600).minimizable(false).maximizable(false).closable(false)
                .skipTaskbar(true).allWorkspaces(true).contentProtection(true).focusable(false).transparent(true)
                .backgroundColor(10, 20, 30, 255).titleBar(WindowOptions.TitleBar.TRANSPARENT).trafficLights(12, 18)
                .shadow(false).backdrop(WindowOptions.Backdrop.MICA).userAgent("swaswa-test").newWindows(WindowOptions.NewWindows.JAVA)
                .downloads(false).shortcuts("Mod+S").toPayload();
        assertEquals(800.0, payload.get("maxWidth"));
        assertEquals(false, payload.get("minimizable"));
        assertEquals(List.of(10, 20, 30, 255), payload.get("backgroundColor"));
        assertEquals("transparent", payload.get("titleBar"));
        assertEquals("mica", payload.get("backdrop"));
        assertEquals("java", payload.get("newWindows"));
        assertEquals(List.of("Mod+S"), payload.get("shortcuts"));
        assertThrows(IllegalArgumentException.class, () -> WindowOptions.html("x").backgroundColor(256, 0, 0, 0));
        // left at their defaults, nothing goes
        var plain = WindowOptions.html("<p>x</p>").toPayload();
        assertFalse(plain.containsKey("minimizable") || plain.containsKey("shortcuts") || plain.containsKey("newWindows"));
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    @Timeout(90)
    void aWindowDoesWhatItIsAskedAndSaysWhatHappened() throws Exception {
        try (App app = App.start()) {
            BlockingQueue<WebViewEvent> events = new LinkedBlockingQueue<>();
            app.subscribe(WebViewEvent.class, e -> {
                if (e instanceof Shortcut || e instanceof NewWindowRequested || e instanceof WindowState || e instanceof PageLoad) events.add(e);
            });
            View view = app.open(WindowOptions.html("<p id=p>page</p>").size(600, 400).userAgent("swaswa-test/1")
                    .newWindows(WindowOptions.NewWindows.JAVA).shortcuts("Mod+S").maxSize(1200, 900).backgroundColor(20, 20, 20, 255)
                    .backdrop(WindowOptions.Backdrop.MICA)).join();
            assertEquals("swaswa-test/1", view.eval("navigator.userAgent").join(), "the page's user agent");

            // a key the window answers itself: the page does nothing with it, Java hears it
            Object handled = view.eval("""
                    (() => { const e = new KeyboardEvent('keydown', { key: 's', code: 'KeyS', ctrlKey: true, bubbles: true, cancelable: true });
                      document.getElementById('p').dispatchEvent(e); return e.defaultPrevented; })()""").join();
            assertEquals(true, handled, "the page did nothing with it");
            assertEquals(new Shortcut(view.id(), "Mod+S"), next(events, Shortcut.class));
            view.setShortcuts("F5").join();
            view.eval("document.dispatchEvent(new KeyboardEvent('keydown', { key: 'F5', code: 'F5', bubbles: true }))").join();
            assertEquals("F5", next(events, Shortcut.class).shortcut(), "the window's keys, changed");

            // a new window, refused and handed to Java
            view.eval("window.open('https://example.com/docs')").join();
            assertEquals("https://example.com/docs", next(events, NewWindowRequested.class).url());

            // the window's settings: each applied without error
            view.setMaxSize(1000, 800).join();
            view.clearMaxSize().join();
            view.setMinimizable(false).join();
            view.setMaximizable(true).join();
            view.setClosable(true).join();
            view.setSkipTaskbar(false).join();
            view.setContentProtection(true).join();
            view.setContentProtection(false).join();
            view.setShadow(true).join();
            view.setBackgroundColor(255, 255, 255, 255).join();
            view.setBackdrop(WindowOptions.Backdrop.ACRYLIC).join();
            view.setBadge(3).join();
            view.setProgress(View.Progress.NORMAL, 40).join();
            view.setProgress(View.Progress.NONE, -1).join();
            view.requestAttention(View.Attention.INFORMATIONAL).join();
            view.center().join();

            // the page: zoom, reload
            double before = ((Number) view.eval("devicePixelRatio").join()).doubleValue();
            view.setZoom(1.5).join();
            double zoomed = before;
            for (int i = 0; i < 50 && zoomed == before; i++) {
                Thread.sleep(100);
                zoomed = ((Number) view.eval("devicePixelRatio").join()).doubleValue();
            }
            assertEquals(before * 1.5, zoomed, 0.01, "zoomed to 150%");
            view.setZoom(1).join();
            view.eval("window.__mark = 1").join();
            events.clear();
            view.reload().join();
            PageLoad loaded;
            do loaded = next(events, PageLoad.class); while (loaded.state() != PageLoad.State.FINISHED);
            assertEquals(null, view.eval("window.__mark === undefined ? null : 1").join(), "a fresh page");

            // minimized and restored: as events, not polled
            view.minimize().join();
            assertTrue(next(events, WindowState.class).minimized(), "minimized");
            view.restore().join();
            WindowState restored;
            do restored = next(events, WindowState.class); while (restored.minimized());
            assertTrue(restored.restored(), "restored: " + restored);
        }
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    @Timeout(60)
    void escapeLeavesFullscreenUnlessTheWindowKeepsIt() throws Exception {
        try (App app = App.start()) {
            BlockingQueue<WebViewEvent> events = new LinkedBlockingQueue<>();
            app.subscribe(WindowState.class, events::add);
            java.util.Queue<String> said = new java.util.concurrent.ConcurrentLinkedQueue<>();
            app.subscribe(LogRecord.class, log -> { if (log.message().contains("escape")) said.add(log.message()); });
            app.subscribe(PageCall.class, call -> said.add("page: " + call));
            String escape = "document.body.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', code: 'Escape', bubbles: true, cancelable: true }))";

            View view = app.open(WindowOptions.html("<body>page</body>").size(500, 300)).join();
            // its page, not the blank one before it (where the window's scripts do not run): what a person presses Escape in
            ViewMethodsTest.until(() -> view.eval("document.body?.textContent ?? null").join(), "page"::equals);
            view.setFullscreen(true).join();
            WindowState state;
            do state = next(events, WindowState.class); while (!state.fullscreen());
            view.eval(escape).join();
            try {
                do state = next(events, WindowState.class); while (state.fullscreen());
            } catch (AssertionError none) {
                throw new AssertionError("Escape did not leave fullscreen; the native side said: " + said + "; geometry " + view.geometry().join());
            }
            assertFalse(state.fullscreen(), "Escape left fullscreen");

            // a page that uses Escape itself keeps it: the window stays fullscreen
            view.setFullscreen(true).join();
            do state = next(events, WindowState.class); while (!state.fullscreen());
            view.eval("document.addEventListener('keydown', e => { if (e.key === 'Escape') e.preventDefault(); }, true); " + escape).join();
            assertNull(events.poll(1500, java.util.concurrent.TimeUnit.MILLISECONDS), "the page used Escape: still fullscreen");
            view.setFullscreen(false).join();

            // a kiosk keeps fullscreen whatever is pressed
            events.clear();
            View kiosk = app.open(WindowOptions.html("<body>kiosk</body>").size(500, 300).fullscreen(true).escapeExitsFullscreen(false)).join();
            ViewMethodsTest.until(() -> kiosk.eval("document.body?.textContent ?? null").join(), "kiosk"::equals);
            kiosk.eval(escape).join();
            WindowState after = (WindowState) events.poll(1500, java.util.concurrent.TimeUnit.MILLISECONDS);
            assertTrue(after == null || after.fullscreen() || !after.view().equals(kiosk.id()), "the kiosk stayed fullscreen: " + after);
            assertTrue(kiosk.geometry().join().fullscreen(), "still fullscreen");
        }
    }

    private static <E extends WebViewEvent> E next(BlockingQueue<WebViewEvent> events, Class<E> type) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            WebViewEvent e = events.poll(15, SECONDS);
            if (e == null) break;
            if (type.isInstance(e)) return type.cast(e);
        }
        throw new AssertionError("no " + type.getSimpleName());
    }
}
