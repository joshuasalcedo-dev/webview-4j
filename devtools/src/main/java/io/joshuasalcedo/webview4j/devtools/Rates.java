package io.joshuasalcedo.webview4j.devtools;

import io.joshuasalcedo.webview4j.Metrics;

/**
 * What the app does per second, between two readings of its {@link Metrics}: frames and kilobytes
 * each way, actions and pages' calls.
 */
record Rates(double framesInPerSecond, double framesOutPerSecond, double kilobytesInPerSecond,
             double kilobytesOutPerSecond, double actionsPerSecond, double pageCallsPerSecond) {

    static final Rates NONE = new Rates(0, 0, 0, 0, 0, 0);

    /** From the earlier reading to the later; none when no time passed between them. */
    static Rates between(Metrics earlier, Metrics later) {
        double seconds = (later.uptime().toNanos() - earlier.uptime().toNanos()) / 1e9;
        if (seconds <= 0) return NONE;
        return new Rates(
                (later.framesIn() - earlier.framesIn()) / seconds,
                (later.framesOut() - earlier.framesOut()) / seconds,
                (later.bytesIn() - earlier.bytesIn()) / 1024.0 / seconds,
                (later.bytesOut() - earlier.bytesOut()) / 1024.0 / seconds,
                (later.actions().count() - earlier.actions().count()) / seconds,
                (later.pageCalls().count() - earlier.pageCalls().count()) / seconds);
    }
}
