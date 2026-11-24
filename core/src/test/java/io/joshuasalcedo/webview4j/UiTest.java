package io.joshuasalcedo.webview4j;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** @webview4j/ui from core's jar, in a page with no bundler, and the page's own window. Windows only, like AppTest. */
@EnabledOnOs(OS.WINDOWS)
class UiTest {

    @Test
    @Timeout(90)
    void anyPageLoadsTheNativeComponentsFromCoresJar() throws Exception {
        String page = """
                <link rel="stylesheet" href="%1$sui.css">
                <div id="root"></div>
                <script type="module">
                  import { React, createRoot, NativeProvider, Button } from "%1$sui.js"
                  createRoot(document.getElementById("root")).render(
                    React.createElement(NativeProvider, { platform: "windows", theme: "light" },
                      React.createElement(Button, { variant: "accent", id: "send" }, "Send")))
                </script>
                """.formatted(App.UI_URL);
        try (App app = App.start()) {
            BlockingQueue<Closed> closed = new LinkedBlockingQueue<>();
            app.subscribe(Closed.class, closed::add);
            View view = app.open(WindowOptions.html(page).headless(true)).join();

            Object text = null;
            long deadline = System.nanoTime() + SECONDS.toNanos(30);
            while (System.nanoTime() < deadline) {
                text = view.eval("document.getElementById('send')?.textContent ?? null").join();
                if (text != null) break;
                Thread.sleep(200);
            }
            assertEquals("Send", text, "the button rendered from <base>ui/ui.js");
            assertEquals("windows", view.eval("document.querySelector('[data-platform]')?.dataset.platform").join());
            // ui.css is applied: the accent button is not the browser's grey default
            Object background = view.eval("getComputedStyle(document.getElementById('send')).backgroundColor").join();
            assertNotEquals("rgb(239, 239, 239)", background);
            assertNotEquals("rgba(0, 0, 0, 0)", background);

            // the page closes its own window, as a TitleBar's close button does
            assertEquals("function", view.eval("typeof window.__webview4j.window.close").join());
            view.eval("window.__webview4j.window.close()").join();
            assertEquals(view.id(), closed.poll(10, SECONDS).view());
        }
    }

    @Test
    @Timeout(60)
    void aWindowWithoutAFrameShowsResizeArrowsAtItsEdges() throws Exception {
        String moveTo = "(() => { document.dispatchEvent(new MouseEvent('mousemove', {clientX: %s, clientY: %s}));"
                + " return getComputedStyle(document.body).cursor; })()";
        try (App app = App.start()) {
            View frameless = app.open(WindowOptions.html("<body style='margin:0;height:100vh'></body>")
                    .headless(true).decorations(false).size(400, 300)).join();
            assertEquals(true, frameless.eval("window.__webview4j_frameless === true").join());
            assertEquals("nwse-resize", frameless.eval(moveTo.formatted(1, 1)).join());
            assertEquals("ew-resize", frameless.eval(moveTo.formatted("innerWidth - 2", 150)).join());
            assertEquals("ns-resize", frameless.eval(moveTo.formatted(200, "innerHeight - 1")).join());
            assertEquals("auto", frameless.eval(moveTo.formatted(200, 150)).join(), "the middle is the page's own");

            // the native theme is the page's prefers-color-scheme: Java switches it, the page follows
            String dark = "matchMedia('(prefers-color-scheme: dark)').matches";
            frameless.setTheme(Theme.DARK).join();
            assertEquals(true, frameless.eval(dark).join());
            frameless.setTheme(Theme.LIGHT).join();
            assertEquals(false, frameless.eval(dark).join());
            View darkFromStart = app.open(WindowOptions.html("<p>dark</p>").headless(true).theme(Theme.DARK)).join();
            assertEquals(true, darkFromStart.eval(dark).join());

            View framed = app.open(WindowOptions.html("<body style='margin:0;height:100vh'></body>").headless(true)).join();
            assertEquals(false, framed.eval("window.__webview4j_frameless === true").join());
            assertEquals("auto", framed.eval(moveTo.formatted(1, 1)).join(), "a framed window's edges are the system's");
        }
    }
}
