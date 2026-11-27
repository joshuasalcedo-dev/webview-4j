package io.joshuasalcedo.swaswa.ffi;

import java.util.concurrent.CompletableFuture;

/** A window an {@link Immediate} draws a UI in. */
public interface ImmediateWindow extends AutoCloseable {

    String id();

    /** The system's frame and title bar, on or off (off: the UI draws its own, {@link Ui#titleBar}). */
    void setDecorations(boolean on);

    /**
     * Where a panel ({@link Immediate#embed}) is in its window's page (CSS pixels); a width or height of 0 hides it.
     * A window of its own: its place and size on the screen (logical pixels).
     */
    void place(double x, double y, double width, double height);

    /** A frame soon: for a change of the app's own the UI should show (a value a worker set). */
    void repaint();

    /** Closes it; {@link #whenClosed()} completes. */
    @Override
    void close();

    /** Completes once it is closed: by {@link #close()}, by the person, or with the app. */
    CompletableFuture<Void> whenClosed();
}
