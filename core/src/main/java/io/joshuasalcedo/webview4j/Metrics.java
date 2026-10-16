package io.joshuasalcedo.webview4j;

import java.time.Duration;
import java.util.List;

/**
 * What the app has done since it started, read at one moment: the frames that crossed between Java
 * and the native side (and their bytes), how long the actions took from Java's ask to the native
 * side's reply, and how long the app took to answer the pages' calls. Rates are worked out by
 * comparing two snapshots: {@code (b.framesIn() - a.framesIn()) / (b.uptime() - a.uptime())}.
 * Read-only: nothing here changes the app. From {@link App#metrics()}.
 *
 * @param uptime how long since the app was made
 * @param framesIn frames the native side sent to Java
 * @param framesOut frames Java sent to the native side
 * @param bytesIn the bytes of the frames in
 * @param bytesOut the bytes of the frames out
 * @param actions round trips of the actions Java asked for (open, eval, navigate...), all of them
 * @param pageCalls the pages' calls the app answered (commands and routes), all of them
 * @param byAction the round trips per action, the most frequent first
 */
public record Metrics(
        Duration uptime,
        long framesIn,
        long framesOut,
        long bytesIn,
        long bytesOut,
        Timing actions,
        Timing pageCalls,
        List<Timing> byAction) {

    public Metrics {
        byAction = List.copyOf(byAction);
    }

    /**
     * How long something took, over every time it happened; the 95th percentile and the slowest
     * over the latest {@value Meter#WINDOW}.
     *
     * @param name what was timed
     * @param count how many times
     * @param lastMillis the latest, in milliseconds
     * @param averageMillis the mean of all of them
     * @param p95Millis the 95th percentile of the latest ones
     * @param maxMillis the slowest of the latest ones
     */
    public record Timing(String name, long count, double lastMillis, double averageMillis, double p95Millis, double maxMillis) {}
}
