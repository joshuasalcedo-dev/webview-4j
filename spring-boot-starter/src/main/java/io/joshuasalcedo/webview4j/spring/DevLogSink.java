package io.joshuasalcedo.webview4j.spring;

import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import io.joshuasalcedo.webview4j.DevLogger;

/**
 * Where the starter's own sources (Logback, the HTTP clients) put what the dev logger shows. They start with the
 * app, before the logger's window is open (it opens once the app is ready): what comes before waits here (the
 * latest {@value #EARLY}), so the start's log and calls are not lost.
 */
public final class DevLogSink {

    /** At most this many entries wait for the logger's window. */
    static final int EARLY = 2000;

    private final boolean on;
    private final ConcurrentLinkedQueue<Map<String, Object>> early = new ConcurrentLinkedQueue<>();
    private volatile DevLogger logger;
    private volatile boolean logback;

    DevLogSink(boolean on) {
        this.on = on;
    }

    /** Whether the dev logger is wanted (webview4j.dev-logger; unset: in development, with a window to show). */
    public boolean on() {
        return on;
    }

    /** An entry in the dev logger's format (see {@link DevLogger#log} and {@link DevLogger#javaCall}). */
    public void add(Map<String, Object> entry) {
        if (!on) return;
        DevLogger now = logger;
        if (now != null) {
            now.add(entry);
            return;
        }
        early.add(entry);
        while (early.size() > EARLY) early.poll();
    }

    /** The logger's window is open: what waited goes to it, and from now on everything does. */
    void attach(DevLogger opened) {
        for (Map<String, Object> e; (e = early.poll()) != null; ) opened.add(e);
        logger = opened;
        for (Map<String, Object> e; (e = early.poll()) != null; ) opened.add(e);
        // Logback gets java.util.logging too (Spring Boot sends it there): the logger's own capture would show it twice
        if (logback) opened.javaLogging(false);
    }

    /** What waits for the window (for the tests). */
    java.util.List<Map<String, Object>> waiting() {
        return java.util.List.copyOf(early);
    }

    void logbackInstalled() {
        logback = true;
        DevLogger now = logger;
        if (now != null) now.javaLogging(false);
    }
}
