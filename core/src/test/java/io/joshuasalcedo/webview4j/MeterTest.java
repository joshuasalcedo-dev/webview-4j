package io.joshuasalcedo.webview4j;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** What the meter counts and how it times: names without view ids, the mean, the 95th percentile. */
class MeterTest {

    @Test
    void anActionIsNamedWithoutItsView() {
        assertEquals("view.eval", Meter.actionName("view.wv-1.eval"));
        assertEquals("view.capture.clicks", Meter.actionName("view.wv-12.capture.clicks"));
        assertEquals("app.open", Meter.actionName("app.open"));
    }

    @Test
    void framesAndBytesAreCountedBothWays() {
        Meter meter = new Meter();
        meter.frameOut(100);
        meter.frameOut(20);
        meter.frameIn(7);

        Metrics metrics = meter.snapshot();
        assertEquals(2, metrics.framesOut());
        assertEquals(120, metrics.bytesOut());
        assertEquals(1, metrics.framesIn());
        assertEquals(7, metrics.bytesIn());
        assertEquals(0, metrics.actions().count());
    }

    @Test
    void timingsHaveTheLatestTheMeanThe95thPercentileAndTheSlowest() {
        Meter meter = new Meter();
        // 1 ms to 100 ms on two views' evals, then one open.
        for (int ms = 1; ms <= 100; ms++) meter.action("view.wv-" + (ms % 2) + ".eval", ms * 1_000_000L);
        meter.action("app.open", 5_000_000L);

        Metrics metrics = meter.snapshot();
        Metrics.Timing evals = metrics.byAction().getFirst();
        assertEquals("view.eval", evals.name());
        assertEquals(100, evals.count());
        assertEquals(100.0, evals.lastMillis());
        assertEquals(50.5, evals.averageMillis(), 1e-9);
        assertEquals(95.0, evals.p95Millis());
        assertEquals(100.0, evals.maxMillis());
        assertEquals(101, metrics.actions().count());
        assertEquals("app.open", metrics.byAction().get(1).name());
    }

    @Test
    void thePercentileIsOverTheLatestOnly() {
        Meter meter = new Meter();
        for (int i = 0; i < Meter.WINDOW; i++) meter.pageCall(1_000_000_000L);
        for (int i = 0; i < Meter.WINDOW; i++) meter.pageCall(1_000_000L);

        Metrics.Timing calls = meter.snapshot().pageCalls();
        assertEquals(2L * Meter.WINDOW, calls.count());
        assertEquals(1.0, calls.p95Millis());
        assertEquals(1.0, calls.maxMillis());
        assertEquals(500.5, calls.averageMillis(), 1e-9);
    }
}
