package io.joshuasalcedo.webview4j;

import static java.util.concurrent.TimeUnit.SECONDS;

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
            t = System.nanoTime();
            List<CompletableFuture<Void>> emits = new ArrayList<>();
            for (int i = 0; i < N; i++) emits.add(view.emit("bench", Map.of("i", i)));
            CompletableFuture.allOf(emits.toArray(CompletableFuture[]::new)).join();
            if (!"done".equals(console.poll(60, SECONDS))) throw new AssertionError("emits went missing");
            report("emit to the page, until all arrived", N / seconds(t), "emits/s");

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

    private static double millis(long start) { return (System.nanoTime() - start) / 1e6; }
    private static double micros(long start) { return (System.nanoTime() - start) / 1e3; }
    private static double seconds(long start) { return (System.nanoTime() - start) / 1e9; }

    private static void report(String what, double value, String unit) {
        System.out.printf("BENCH %-52s %,12.1f %s%n", what, value, unit);
    }
}
