package io.joshuasalcedo.webview4j.devtools.demo;

import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.Capture;
import io.joshuasalcedo.webview4j.WindowOptions;
import io.joshuasalcedo.webview4j.devtools.DevWindow;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * A sample app with the dev window beside it: a page that animates (its frames per second show in
 * the window), calls Java on a button and in bursts (the pages' calls and their timings show), and
 * logs to its console and from Java (the log shows both). {@code ./gradlew :devtools:runDemo}.
 */
public final class Demo {

    private static final Logger LOG = Logger.getLogger(Demo.class.getName());

    private static final String PAGE = """
            <!doctype html>
            <html>
            <head>
              <meta charset="utf-8">
              <title>webview-4j demo</title>
              <style>
                body { font-family: system-ui, sans-serif; margin: 2rem; background: #0f172a; color: #e2e8f0; }
                .spinner { width: 64px; height: 64px; border-radius: 50%; border: 6px solid #334155;
                           border-top-color: #38bdf8; animation: spin 1s linear infinite; }
                @keyframes spin { to { transform: rotate(360deg); } }
                button { margin: .25rem .5rem .25rem 0; padding: .5rem 1rem; border-radius: .5rem; border: 0;
                         background: #38bdf8; color: #0f172a; font-weight: 600; cursor: pointer; }
                pre { background: #1e293b; padding: 1rem; border-radius: .5rem; }
              </style>
            </head>
            <body>
              <h1>webview-4j demo</h1>
              <div class="spinner"></div>
              <p>
                <button id="ping">Ping Java</button>
                <button id="burst">100 pings</button>
                <button id="warn">console.warn</button>
                <button id="busy">Busy the page 300 ms</button>
              </p>
              <pre id="out">Press a button.</pre>
              <script>
                const out = document.getElementById('out');
                const ping = () => window.__webview4j.invoke('demo.ping', { at: Date.now() });
                document.getElementById('ping').onclick = async () => {
                  const answer = await ping();
                  out.textContent = JSON.stringify(answer, null, 2);
                };
                document.getElementById('burst').onclick = async () => {
                  const started = performance.now();
                  await Promise.all(Array.from({ length: 100 }, ping));
                  out.textContent = '100 pings in ' + (performance.now() - started).toFixed(1) + ' ms';
                  console.info('burst done');
                };
                document.getElementById('warn').onclick = () => console.warn('a warning from the page');
                document.getElementById('busy').onclick = () => {
                  const until = performance.now() + 300;
                  while (performance.now() < until) {}
                  console.log('the page was busy for 300 ms: watch its frames per second dip');
                };
              </script>
            </body>
            </html>
            """;

    private Demo() {}

    public static void main(String[] args) {
        AtomicLong pings = new AtomicLong();
        try (App app = App.start()) {
            DevWindow.attach(app);
            app.handle("demo.ping", call -> {
                long count = pings.incrementAndGet();
                if (count % 50 == 0) LOG.info("pinged " + count + " times");
                return Map.of("pong", count, "thread", Thread.currentThread().toString());
            });
            app.open(WindowOptions.html(PAGE).title("webview-4j demo").size(720, 520)
                    .capture(Capture.CONSOLE, Capture.CLICKS)).join();
            LOG.info("the demo is open: close its window to quit");
            app.whenStopped().join();
        }
        System.exit(0);
    }
}
