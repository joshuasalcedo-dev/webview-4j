package io.joshuasalcedo.swaswa.ffi;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.foreign.MemorySegment;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Each of a view's methods, on a real window: what it was asked reaches the window (as Windows has it) or the page,
 * and what it reports is true.
 */
@EnabledOnOs(OS.WINDOWS)
@Timeout(120)
class ViewMethodsTest {

    /** Waits until {@code check} holds, up to 10 seconds; the last value it saw, for the message. */
    static <T> T until(Supplier<T> value, java.util.function.Predicate<T> check) throws InterruptedException {
        T seen = value.get();
        for (int i = 0; i < 100 && !check.test(seen); i++) {
            Thread.sleep(100);
            seen = value.get();
        }
        return seen;
    }

    /**
     * Brings {@code view} to the front, for a test that needs it there; skips the test when Windows refuses (another
     * app is in use: a background app may not take the foreground then), instead of failing it.
     */
    static void front(View view) {
        org.junit.jupiter.api.Assumptions.assumeTrue(view.focus().join(),
                "another app holds the foreground (someone is using the machine): Windows will not hand it to this one");
    }

    static Object eval(View view, String script) {
        return view.eval(script).join();
    }

    @Test
    void pagesNavigateBackAndForwardFromServedFiles(@TempDir Path dir) throws Exception {
        Path site = Files.createDirectories(dir.resolve("site"));
        Files.writeString(site.resolve("one.html"), "<!doctype html><title>one</title><p>one</p>");
        Files.writeString(site.resolve("two.html"), "<!doctype html><title>two</title><p>two</p>");
        try (App app = App.start(); URLClassLoader loader = new URLClassLoader(new URL[] {dir.toUri().toURL()})) {
            app.serveShared("site", loader);
            app.serveShared("site", loader);   // the same again: nothing
            assertThrows(IllegalStateException.class, () -> app.serveShared("other", loader), "one shared directory per app");

            View view = app.open(WindowOptions.html("<title>start</title>").headless(true)).join();
            view.navigate(App.SHARED_URL + "one.html").join();
            assertEquals("one", until(() -> eval(view, "document.title"), "one"::equals), "navigate: a served page");
            view.navigate(App.SHARED_URL + "two.html").join();
            assertEquals("two", until(() -> eval(view, "document.title"), "two"::equals));

            view.back().join();
            assertEquals("one", until(() -> eval(view, "document.title"), "one"::equals), "back");
            view.forward().join();
            assertEquals("two", until(() -> eval(view, "document.title"), "two"::equals), "forward");

            // the browsing data of a served page: what it kept is gone
            eval(view, "localStorage.setItem('kept', 'yes')");
            assertEquals("yes", eval(view, "localStorage.getItem('kept')"));
            view.clearBrowsingData().join();
            view.reload().join();
            assertNull(until(() -> eval(view, "localStorage.getItem('kept')"), java.util.Objects::isNull), "clearBrowsingData");

            view.loadHtml("<title>loaded</title><p id=p>html</p>").join();
            assertEquals("html", until(() -> eval(view, "document.getElementById('p')?.textContent ?? null"), "html"::equals), "loadHtml");
        }
    }

    @Test
    void printAndTheme() throws Exception {
        try (App app = App.start()) {
            View view = app.open(WindowOptions.html("<title>t</title><p>page</p>").headless(true)).join();

            // print: the page's own print (its print dialog is the page's, inside the window): heard, not shown
            eval(view, "window.__printed = 0; window.print = () => { window.__printed++; }");
            view.print().join();
            assertEquals(1, ((Number) until(() -> eval(view, "window.__printed"), n -> ((Number) n).intValue() > 0)).intValue(), "print");

            view.setTheme(Theme.DARK).join();
            assertEquals(true, until(() -> eval(view, "matchMedia('(prefers-color-scheme: dark)').matches"), Boolean.TRUE::equals), "dark");
            view.setTheme(Theme.LIGHT).join();
            assertEquals(false, until(() -> eval(view, "matchMedia('(prefers-color-scheme: dark)').matches"), Boolean.FALSE::equals), "light");
            view.setTheme(Theme.SYSTEM).join();
        }
    }

    @Test
    void theWindowIsWhereAndHowItWasAsked() throws Exception {
        try (App app = App.start()) {
            BlockingQueue<WindowState> states = new LinkedBlockingQueue<>();
            app.subscribe(WindowState.class, states::add);
            java.util.Queue<Focused> focused = new java.util.concurrent.ConcurrentLinkedQueue<>();
            app.subscribe(Focused.class, focused::add);
            View view = app.open(WindowOptions.html("<p>w</p>").title("swaswa test: window").size(500, 400)).join();
            MemorySegment hwnd = Win32.window("swaswa test: window");

            List<Monitor> monitors = view.monitors().join();
            assertFalse(monitors.isEmpty(), "monitors");
            assertEquals(1, monitors.stream().filter(Monitor::primary).count(), "one main monitor");
            Monitor main = monitors.stream().filter(Monitor::primary).findFirst().orElseThrow();
            assertTrue(main.width() > 0 && main.height() > 0 && main.scaleFactor() > 0, main.toString());

            view.setPosition(main.x() + 60, main.y() + 70).join();
            Geometry placed = until(() -> view.geometry().join(), g -> Math.abs(g.x() - main.x() - 60) < 2 && Math.abs(g.y() - main.y() - 70) < 2);
            assertEquals(main.x() + 60, placed.x(), 2, "setPosition: logical pixels");
            assertEquals(main.y() + 70, placed.y(), 2);
            assertTrue(main.covers(placed.x(), placed.y(), placed.width(), placed.height()) > 0, "on the main monitor");

            double scale = placed.scaleFactor();
            int px = (int) Math.round((main.x() + 100) * scale), py = (int) Math.round((main.y() + 90) * scale);
            view.setBounds(px, py, (int) Math.round(640 * scale), (int) Math.round(480 * scale)).join();
            Geometry bounded = until(() -> view.geometry().join(), g -> Math.abs(g.x() - main.x() - 100) < 2);
            assertEquals(main.x() + 100, bounded.x(), 2, "setBounds: physical pixels, the outer frame");
            assertEquals(main.y() + 90, bounded.y(), 2);
            assertTrue(bounded.width() <= 640 && bounded.width() > 560, "the inner size within the outer: " + bounded);

            view.maximize().join();
            assertTrue(next(states, WindowState::maximized), "maximize");
            assertTrue(view.geometry().join().maximized());
            view.restore().join();
            assertTrue(next(states, s -> !s.maximized() && !s.minimized()), "restored");

            // the frame and title bar, as Windows has them
            int titleBar = Win32.frameHeight(hwnd);
            assertTrue(titleBar > 20, "a title bar: " + titleBar + " px");
            view.setDecorations(false).join();
            assertTrue(until(() -> Win32.frameHeight(hwnd), h -> h < 10) < 10, "no title bar: the page is the whole window");
            assertEquals(true, eval(view, "window.__webview4j_frameless === true"), "the page resizes the frameless window");
            view.setDecorations(true).join();
            assertEquals(titleBar, (int) until(() -> Win32.frameHeight(hwnd), h -> h == titleBar), "the title bar back");
            assertEquals(false, eval(view, "window.__webview4j_frameless === true"));

            view.setFocusable(false).join();
            assertNotEquals(0L, until(() -> Win32.exStyle(hwnd) & Win32.WS_EX_NOACTIVATE, v -> v != 0), "not focusable: never activated");
            view.setFocusable(true).join();
            assertEquals(0L, until(() -> Win32.exStyle(hwnd) & Win32.WS_EX_NOACTIVATE, v -> v == 0), "focusable again");

            // Windows has no workspaces to show it on: asked, and nothing else changes
            view.setVisibleOnAllWorkspaces(true).join();
            view.setVisibleOnAllWorkspaces(false).join();
            assertTrue(Win32.visible(hwnd), "still shown");

            // focus: of two windows, the one asked
            View other = app.open(WindowOptions.html("<p>other</p>").title("swaswa test: other").size(300, 200)).join();
            boolean came = other.focus().join();
            MemorySegment otherHwnd = Win32.window("swaswa test: other");
            assertEquals(came, Win32.foreground().address() == otherHwnd.address(), "focus() says whether it came to the front, truly");
            org.junit.jupiter.api.Assumptions.assumeTrue(came, "another app holds the foreground: the rest needs it");
            assertEquals(otherHwnd.address(), until(() -> Win32.foreground().address(), a -> a == otherHwnd.address()), "the other in front");
            assertTrue(until(() -> other.geometry().join().focused(), f -> f), "and says so, though its webview has the keyboard");
            focused.clear();
            front(view);
            assertEquals(hwnd.address(), until(() -> Win32.foreground().address(), a -> a == hwnd.address()), "focus: in front again");
            assertTrue(until(() -> view.geometry().join().focused(), f -> f));
            assertFalse(other.geometry().join().focused());
            // the events say the same, once each: the other lost it, this one has it (no flicker as its webview takes the keyboard)
            List<Focused> heard = until(() -> List.copyOf(focused),
                    h -> h.contains(new Focused(view.id(), true)) && h.contains(new Focused(other.id(), false)));
            assertTrue(heard.contains(new Focused(view.id(), true)) && heard.contains(new Focused(other.id(), false)), heard.toString());
            // no flicker: once it has the focus, it does not lose it (a loss from before may arrive late)
            int gained = heard.indexOf(new Focused(view.id(), true));
            assertFalse(heard.subList(gained, heard.size()).contains(new Focused(view.id(), false)), "no flicker: " + heard);
        }
    }

    @Test
    void theDevToolsProtocolAnswersAndReportsEvents() throws Exception {
        try (App app = App.start()) {
            BlockingQueue<DevToolsEvent> events = new LinkedBlockingQueue<>();
            app.subscribe(DevToolsEvent.class, events::add);
            View view = app.open(WindowOptions.html("<p>cdp</p>").headless(true).devtools(true)).join();

            @SuppressWarnings("unchecked")
            record Evaluate(String expression, boolean returnByValue) {}
            Map<String, Object> answer = (Map<String, Object>) view.devTools("Runtime.evaluate", new Evaluate("6 * 7", true)).join();
            assertEquals(42, ((Number) ((Map<?, ?>) answer.get("result")).get("value")).intValue(), "a method's result");

            view.listenDevTools("Runtime.consoleAPICalled").join();
            view.devTools("Runtime.enable").join();
            eval(view, "console.log('heard by the protocol')");
            DevToolsEvent heard = null;
            for (int i = 0; i < 20 && heard == null; i++) {
                DevToolsEvent e = events.poll(1, SECONDS);
                if (e != null && e.event().equals("Runtime.consoleAPICalled") && e.params().toString().contains("heard by the protocol")) heard = e;
            }
            assertNotNull(heard, "an event listened to");
            assertEquals(view.id(), heard.view());
            assertTrue(heard.params().toString().contains("heard by the protocol"), heard.params().toString());

            // the developer tools: opened, then asked to close (on Windows WebView2 cannot close them: no error)
            view.openDevTools().join();
            view.closeDevTools().join();
            assertEquals(java.util.Optional.empty(), view.devToolsOpen().join(), "Windows cannot say whether they are open");
        }
    }

    @Test
    void viewsAreEqualByIdentity() throws Exception {
        try (App app = App.start()) {
            View view = app.open(WindowOptions.html("<p>v</p>").headless(true)).join();
            View same = app.view(view.id());
            assertEquals(view, same);
            assertEquals(view.hashCode(), same.hashCode());
            View other = app.open(WindowOptions.html("<p>o</p>").headless(true)).join();
            assertNotEquals(view, other);
        }
    }

    private static boolean next(BlockingQueue<WindowState> states, java.util.function.Predicate<WindowState> check) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            WindowState s = states.poll(10, SECONDS);
            if (s == null) return false;
            if (check.test(s)) return true;
        }
        return false;
    }
}
