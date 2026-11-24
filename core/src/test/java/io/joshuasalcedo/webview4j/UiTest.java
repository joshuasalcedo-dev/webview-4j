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
}
