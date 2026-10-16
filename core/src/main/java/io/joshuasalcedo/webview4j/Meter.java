package io.joshuasalcedo.webview4j;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Counts and times what crosses the app, for {@link Metrics}: cheap enough to be always on (adders
 * for the counts, a small lock per timing). Package-private: the app records, only snapshots leave.
 */
final class Meter {

    /** How many of the latest timings the percentile and the slowest are taken over. */
    static final int WINDOW = 512;

    private final long started = System.nanoTime();
    private final LongAdder framesIn = new LongAdder();
    private final LongAdder framesOut = new LongAdder();
    private final LongAdder bytesIn = new LongAdder();
    private final LongAdder bytesOut = new LongAdder();
    private final Timer actions = new Timer("actions");
    private final Timer pageCalls = new Timer("page calls");
    private final Map<String, Timer> byAction = new ConcurrentHashMap<>();

    void frameIn(int bytes) {
        framesIn.increment();
        bytesIn.add(bytes);
    }

    void frameOut(int bytes) {
        framesOut.increment();
        bytesOut.add(bytes);
    }

    /** An action's round trip: {@code subject} as sent, its view id left out of the name. */
    void action(String subject, long nanos) {
        actions.add(nanos);
        byAction.computeIfAbsent(actionName(subject), Timer::new).add(nanos);
    }

    void pageCall(long nanos) {
        pageCalls.add(nanos);
    }

    Metrics snapshot() {
        List<Metrics.Timing> each = new ArrayList<>();
        for (Timer timer : byAction.values()) each.add(timer.timing());
        each.sort(Comparator.comparingLong(Metrics.Timing::count).reversed().thenComparing(Metrics.Timing::name));
        return new Metrics(Duration.ofNanos(System.nanoTime() - started),
                framesIn.sum(), framesOut.sum(), bytesIn.sum(), bytesOut.sum(),
                actions.timing(), pageCalls.timing(), each);
    }

    /** {@code view.wv-1.eval} is {@code view.eval}: one name for the action on every view. */
    static String actionName(String subject) {
        String[] tokens = subject.split("\\.");
        if (tokens.length >= 3 && tokens[0].equals("view")) {
            return "view." + String.join(".", Arrays.copyOfRange(tokens, 2, tokens.length));
        }
        return subject;
    }

    /** One thing timed: every time it happened, and the latest {@value #WINDOW} for the percentile. */
    static final class Timer {
        private final String name;
        private final long[] latest = new long[WINDOW];
        private long count;
        private long total;
        private long last;

        Timer(String name) {
            this.name = name;
        }

        synchronized void add(long nanos) {
            latest[(int) (count % WINDOW)] = nanos;
            count++;
            total += nanos;
            last = nanos;
        }

        synchronized Metrics.Timing timing() {
            if (count == 0) return new Metrics.Timing(name, 0, 0, 0, 0, 0);
            long[] window = Arrays.copyOf(latest, (int) Math.min(count, WINDOW));
            Arrays.sort(window);
            long p95 = window[Math.min(window.length - 1, (int) Math.ceil(window.length * 0.95) - 1)];
            return new Metrics.Timing(name, count, millis(last), millis(total) / count, millis(p95), millis(window[window.length - 1]));
        }

        private static double millis(long nanos) {
            return nanos / 1_000_000.0;
        }
    }
}
