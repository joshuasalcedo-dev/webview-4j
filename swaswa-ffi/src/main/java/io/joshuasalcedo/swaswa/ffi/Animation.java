package io.joshuasalcedo.swaswa.ffi;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A canvas Java animates ({@link View#animate(String, Animation.Painter)}): each frame painted for the time it is
 * shown, sent once the one before is on the canvas. A shown page puts each at the display's refresh, so it runs at
 * the display's rate (60, 120, 144 frames a second), in step with it; a page that draws slower slows it, never
 * floods it. It runs until {@link #stop()}, until the painter fails, or until the window closes.
 */
public final class Animation {

    /** Paints the frame shown {@code seconds} after the animation started. */
    @FunctionalInterface
    public interface Painter {
        Frame paint(double seconds) throws Exception;
    }

    private final AtomicBoolean running = new AtomicBoolean(true);
    private final CompletableFuture<Long> stopped = new CompletableFuture<>();
    private volatile double framesPerSecond;
    private volatile long frames;

    Animation(View view, String canvas, Painter painter) {
        Objects.requireNonNull(painter, "painter");
        Thread.ofPlatform().daemon().name("swaswa-animation-" + view.id() + "-" + canvas).start(() -> run(view, canvas, painter));
    }

    private void run(View view, String canvas, Painter painter) {
        long started = System.nanoTime(), second = started;
        long inSecond = 0;
        try {
            while (running.get()) {
                Frame frame = painter.paint((System.nanoTime() - started) / 1e9);
                view.drawFrame(canvas, frame).join();
                frames++;
                inSecond++;
                long now = System.nanoTime();
                if (now - second >= 500_000_000L) {   // measured over half a second
                    framesPerSecond = inSecond * 1e9 / (now - second);
                    second = now;
                    inSecond = 0;
                }
            }
            stopped.complete(frames);
        } catch (Throwable failed) {
            running.set(false);
            // the window closed, the canvas went, or the painter failed: stopped, saying why
            stopped.completeExceptionally(failed instanceof java.util.concurrent.CompletionException c && c.getCause() != null ? c.getCause() : failed);
        }
    }

    /** Stops after the frame on its way; {@link #whenStopped()} completes then. */
    public void stop() {
        running.set(false);
    }

    public boolean running() {
        return running.get();
    }

    /** Frames drawn a second, measured over the last half second (0 until then). */
    public double framesPerSecond() {
        return framesPerSecond;
    }

    /** Frames drawn so far. */
    public long frames() {
        return frames;
    }

    /** Completes with how many frames were drawn once it stops; fails with why if it stopped on its own (a closed window). */
    public CompletableFuture<Long> whenStopped() {
        return stopped;
    }
}
