package io.joshuasalcedo.swaswa.ffi;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * A process the app started ({@link App#process(String, ProcessBuilder)}), tied to its life: it ends when the app
 * closes and, on Windows, however the app ends (a crash, the Task Manager), with whatever it started (a Job Object).
 * Each line it writes comes as a {@link ProcessOutput}, its end as a {@link ProcessExited}.
 */
public final class ChildProcess {

    private final String name;
    private final Process process;
    private final boolean tied;
    private final Charset charset;
    private final Instant started = Instant.now();

    ChildProcess(String name, Process process, boolean tied, Charset charset) {
        this.name = name;
        this.process = process;
        this.tied = tied;
        this.charset = charset;
    }

    /** The name the app gave it: its events carry it. */
    public String name() {
        return name;
    }

    public long pid() {
        return process.pid();
    }

    /** Whether the system ends it with the app however the app ends (Windows); elsewhere only when the app closes. */
    public boolean tiedToApp() {
        return tied;
    }

    public boolean running() {
        return process.isAlive();
    }

    Instant started() {
        return started;
    }

    /** Writes {@code text} to its input, as it is (add the line break a line needs). */
    public ChildProcess write(String text) {
        Objects.requireNonNull(text, "text");
        try {
            OutputStream in = process.getOutputStream();
            in.write(text.getBytes(charset));
            in.flush();
        } catch (IOException e) {
            throw new SystemException("could not write to " + name + ": " + e.getMessage(),
                    "It has ended, or closed its input: check running() first, or wait for onExit().", null, e);
        }
        return this;
    }

    /** Ends its input: a program reading until the end goes on (sort, a compiler reading a file from its input). */
    public ChildProcess closeInput() {
        try {
            process.getOutputStream().close();
        } catch (IOException e) {
            // already closed, or it ended: nothing more to say to it either way
        }
        return this;
    }

    /** Ends it, and what it started: asked first, then forced after {@code grace}. Completes with its exit code. */
    public CompletableFuture<Integer> kill(Duration grace) {
        process.descendants().forEach(ProcessHandle::destroy);
        process.destroy();
        return process.onExit().completeOnTimeout(process, grace.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
                .thenCompose(p -> {
                    if (p.isAlive()) {
                        p.descendants().forEach(ProcessHandle::destroyForcibly);
                        p.destroyForcibly();
                        return p.onExit();
                    }
                    return CompletableFuture.completedFuture(p);
                })
                .thenApply(Process::exitValue);
    }

    /** {@link #kill(Duration)} with 3 seconds to end by itself. */
    public CompletableFuture<Integer> kill() {
        return kill(Duration.ofSeconds(3));
    }

    /** Completes with its exit code once it has ended. */
    public CompletableFuture<Integer> onExit() {
        return process.onExit().thenApply(Process::exitValue);
    }

    @Override
    public String toString() {
        return "ChildProcess[" + name + ", pid " + pid() + (running() ? ", running" : ", ended") + "]";
    }
}
