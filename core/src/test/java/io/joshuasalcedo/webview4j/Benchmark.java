package io.joshuasalcedo.webview4j;

import static java.util.concurrent.TimeUnit.SECONDS;

import static io.joshuasalcedo.webview4j.ui.Ui.*;

import io.joshuasalcedo.webview4j.ui.Component;
import io.joshuasalcedo.webview4j.ui.Screen;
import io.joshuasalcedo.webview4j.ui.Ui;
import io.joshuasalcedo.webview4j.ui.UiWindow;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * How fast each path is, in a real hidden window. Not part of the build: run it with
 * {@code ./gradlew test --tests '*Benchmark' -Pbenchmark}; the numbers are in the test's output.
 */
@EnabledOnOs(OS.WINDOWS)
@EnabledIfSystemProperty(named = "benchmark", matches = "true")
class Benchmark {

    private static final int N = 2_000;

    @Test
    @Timeout(300)
    void measure() throws Exception {
        long t0 = System.nanoTime();
        try (App app = App.start()) {
            report("App.start() until ready", millis(t0), "ms");

            BlockingQueue<String> console = new LinkedBlockingQueue<>();
            app.subscribe(LogRecord.class, log -> {
                if (log.source() == LogRecord.Source.PAGE && log.message().startsWith("bench ")) console.add(log.message().substring(6));
            });
            app.handle("bench.echo", call -> call.args());

            long open = System.nanoTime();
            View view = app.open(WindowOptions.html("<p>bench</p>").headless(true)).join();
            report("open a window (headless)", millis(open), "ms");
            view.eval("1").join();
            report("open until the page answers eval", millis(open), "ms");

            // ---- Java -> native -> Java: an action that touches no page ----
            for (int i = 0; i < 500; i++) app.views().join();
            long t = System.nanoTime();
            for (int i = 0; i < N; i++) app.views().join();
            report("action round trip, one at a time (window list)", micros(t) / N, "µs");
            t = System.nanoTime();
            List<CompletableFuture<List<ViewInfo>>> many = new ArrayList<>();
            for (int i = 0; i < N; i++) many.add(app.views());
            CompletableFuture.allOf(many.toArray(CompletableFuture[]::new)).join();
            report("actions in flight together", N / seconds(t), "actions/s");

            // ---- Java -> page -> Java: eval ----
            for (int i = 0; i < 200; i++) view.eval("1").join();
            t = System.nanoTime();
            for (int i = 0; i < N; i++) view.eval("1").join();
            report("eval round trip, one at a time", micros(t) / N, "µs");

            // ---- page -> Java -> page: invoke ----
            String script = """
                    (async () => {
                      const b = window.__webview4j, n = %d;
                      for (let i = 0; i < 200; i++) await b.invoke('bench.echo', i);
                      let t = performance.now();
                      for (let i = 0; i < n; i++) await b.invoke('bench.echo', {i});
                      const sequential = (performance.now() - t) * 1000 / n;
                      t = performance.now();
                      await Promise.all(Array.from({length: n}, (_, i) => b.invoke('bench.echo', {i})));
                      const parallel = n / ((performance.now() - t) / 1000);
                      const big = 'x'.repeat(1024 * 1024);
                      t = performance.now();
                      const back = await b.invoke('bench.echo', {big});
                      const megabyte = performance.now() - t;
                      if (back.big.length !== big.length) throw new Error('the payload changed');
                      console.info('bench ' + JSON.stringify({sequential, parallel, megabyte}));
                    })()""".formatted(N);
            view.eval(script).join();
            Map<String, Object> invoke = Json.object(console.poll(120, SECONDS));
            report("invoke round trip from the page, one at a time", ((Number) invoke.get("sequential")).doubleValue(), "µs");
            report("invokes in flight together", ((Number) invoke.get("parallel")).doubleValue(), "calls/s");
            report("invoke with 1 MB there and back", ((Number) invoke.get("megabyte")).doubleValue(), "ms");

            // ---- Java -> page: emit ----
            view.eval("window.__got = 0; window.__webview4j.listen('bench', () => { if (++window.__got === %d) console.info('bench done'); })"
                    .formatted(N)).join();
            Thread.sleep(500);   // emits before the page's first poll arrives are dropped, as with no listeners
            t = System.nanoTime();
            List<CompletableFuture<Void>> emits = new ArrayList<>();
            for (int i = 0; i < N; i++) emits.add(view.emit("bench", Map.of("i", i)));
            CompletableFuture.allOf(emits.toArray(CompletableFuture[]::new)).join();
            if (!"done".equals(console.poll(60, SECONDS))) throw new AssertionError("emits went missing");
            report("emit to the page, until all arrived", N / seconds(t), "emits/s");

            // ---- Java -> page -> Java: one push at a time, the page answering on its console ----
            view.eval("window.__webview4j.listen('ping', n => console.info('bench pong ' + n))").join();
            for (int i = 0; i < 200; i++) {
                view.emit("ping", i);
                console.poll(10, SECONDS);
            }
            t = System.nanoTime();
            for (int i = 0; i < 500; i++) {
                view.emit("ping", i);
                if (console.poll(10, SECONDS) == null) throw new AssertionError("a push went missing");
            }
            report("push to the page and its answer, one at a time", micros(t) / 500, "µs");

            // ---- page -> Java: events ----
            CountDownLatch arrived = new CountDownLatch(N);
            try (Subscription counting = view.subscribe(LogRecord.class, log -> {
                if (log.message().startsWith("event ")) arrived.countDown();
            })) {
                t = System.nanoTime();
                view.eval("for (let i = 0; i < %d; i++) console.info('event ' + i)".formatted(N)).join();
                if (!arrived.await(60, SECONDS)) throw new AssertionError("events went missing");
                report("page events to a Java subscriber", N / seconds(t), "events/s");
            }
        }
    }

    /** A screen built in Java: a counter, and as many rows as asked. */
    static final class Counter implements Screen {
        volatile int count;
        volatile int rows;

        @Override
        public Component<?> render() {
            List<Component<?>> lines = new ArrayList<>();
            for (int i = 0; i < rows; i++) lines.add(text("row " + i));
            return column(button("Add").onClick(() -> count++), text("count " + count), column(lines.toArray(Component<?>[]::new)));
        }
    }

    /** What apps do on top of the transport: Java screens, the store, grammar, the frame codec, windows. */
    @Test
    @Timeout(300)
    void measureFeatures() throws Exception {
        // ---- the frame codec, Java only ----
        Message frame = Message.of("view.wv-1.eval").with("Reply-To", "_inbox.42").withText("document.title", "text/javascript");
        byte[] bytes = frame.encode();
        for (int i = 0; i < 20_000; i++) Message.decode(frame.encode());
        long t = System.nanoTime();
        for (int i = 0; i < 200_000; i++) Message.decode(frame.encode());
        report("frame encode + decode (" + bytes.length + " bytes)", micros(t) / 200_000 * 1000, "ns");

        // ---- the local store (redb), through FFM ----
        Path dir = Files.createTempDirectory("webview4j-bench");
        try (NativeStore store = NativeStore.open(dir.resolve("bench.redb"))) {
            byte[] value = new byte[256];
            t = System.nanoTime();
            for (int i = 0; i < N; i++) store.put("key." + i, value);
            report("store put, 256 bytes, each its own transaction", micros(t) / N, "µs");
            t = System.nanoTime();
            for (int i = 0; i < N; i++) store.get("key." + i).orElseThrow();
            report("store get", micros(t) / N, "µs");
            t = System.nanoTime();
            int keys = store.keys("key.").size();
            report("store keys by prefix (" + keys + " keys)", millis(t), "ms");
        }

        // ---- spelling and grammar (Harper), through FFM ----
        Grammar grammar = Grammar.english();
        String paragraph = "This is an test of the grammer checker. It have a few mistake in it, and it should find them quick. "
                .repeat(5);
        grammar.check(paragraph);
        t = System.nanoTime();
        for (int i = 0; i < 100; i++) grammar.check(paragraph);
        report("grammar check, " + paragraph.length() + " characters", millis(t) / 100, "ms");

        long t0 = System.nanoTime();
        try (App app = App.start()) {
            report("App.start() again, native library loaded", millis(t0), "ms");

            // ---- the first window, the engine warmed while the app got ready (app.warm) ----
            Thread.sleep(1_000);   // the app's own start (Spring, loading data...)
            t = System.nanoTime();
            View main = app.open(WindowOptions.html("<p>main</p>").headless(true)).join();
            main.eval("1").join();
            report("open the first window, engine warmed meanwhile", millis(t), "ms");

            // ---- windows: after the first, a plain one and an owned one ----
            t = System.nanoTime();
            View second = app.open(WindowOptions.html("<p>second</p>").headless(true)).join();
            second.eval("1").join();
            report("open another window until its page answers", millis(t), "ms");
            t = System.nanoTime();
            View tool = app.open(WindowOptions.html("<p>tool</p>").owner(main).headless(true)).join();
            tool.eval("1").join();
            report("open an owned window until its page answers", millis(t), "ms");
            main.close().join();
            second.close().join();

            // ---- a Java screen: a click, its handler, the new tree, drawn ----
            Counter screen = new Counter();
            t = System.nanoTime();
            UiWindow window = Ui.open(app, screen, o -> o.headless(true).size(800, 600)).join();
            View view = window.view();
            until(view, "count 0");
            report("Ui.open until the screen shows", millis(t), "ms");
            String click = "[...document.querySelectorAll('button')].find(b => b.textContent === 'Add').click()";
            for (int i = 1; i <= 50; i++) {
                view.eval(click).join();
                until(view, "count " + i);
            }
            t = System.nanoTime();
            for (int i = 51; i <= 250; i++) {
                view.eval(click).join();
                until(view, "count " + i);
            }
            report("click -> Java handler -> drawn again", millis(t) / 200, "ms");

            // ---- a big tree, pushed from Java ----
            for (int rows : new int[] {100, 1_000, 5_000}) {
                screen.rows = rows;
                t = System.nanoTime();
                window.refresh();
                until(view, "row " + (rows - 1));
                report("refresh with " + rows + " rows until drawn", millis(t), "ms");
            }
        } finally {
            try (var files = Files.walk(dir)) {
                files.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    /** Waits for text on the page, checking every few milliseconds (the wait is part of what is measured). */
    private static void until(View view, String text) throws InterruptedException {
        String script = "document.body.innerText.includes(" + Json.write(text) + ")";
        for (int i = 0; i < 20_000; i++) {
            if (Boolean.TRUE.equals(view.eval(script).join())) return;
            Thread.sleep(1);
        }
        throw new AssertionError("never showed '" + text + "'");
    }

    private static double millis(long start) { return (System.nanoTime() - start) / 1e6; }
    private static double micros(long start) { return (System.nanoTime() - start) / 1e3; }
    private static double seconds(long start) { return (System.nanoTime() - start) / 1e9; }

    private static void report(String what, double value, String unit) {
        System.out.printf("BENCH %-52s %,12.1f %s%n", what, value, unit);
    }
}
