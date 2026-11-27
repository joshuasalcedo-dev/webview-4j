package io.joshuasalcedo.swaswa.ffi;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Something that draws an immediate-mode UI ({@link Ui}): {@link Egui} (egui, native, drawn with the GPU) now; others
 * later, behind the same interface, so an app's frame functions work with any.
 */
public interface Immediate {

    /**
     * How a window opens: its title, its size (logical pixels), and whether it has the system's frame and title bar
     * (true unless {@link #frameless()}: then the UI draws its own, {@link Ui#titleBar} or {@link Ui#dragRegion}, and the
     * window is sized from its edges).
     */
    record Options(String title, double width, double height, boolean decorations) {
        public Options {
            Objects.requireNonNull(title, "title");
            if (width <= 0 || height <= 0) throw new IllegalArgumentException("a window's size is positive");
        }

        public static Options of(String title, double width, double height) {
            return new Options(title, width, height, true);
        }

        /** Without the system's frame and title bar. */
        public Options frameless() {
            return new Options(title, width, height, false);
        }
    }

    /**
     * A window as {@code options} say, whose UI {@code frame} draws: every frame, from the app's own state. Frames come
     * when something happens (input, a {@link ImmediateWindow#repaint()}, an animation) and not otherwise.
     * {@code frame} runs on the UI's own thread: it should be quick, as a frame is.
     */
    ImmediateWindow open(Options options, Consumer<Ui> frame);

    /**
     * A panel inside {@code host}'s window, at {@code x}, {@code y} in its page, {@code width} by {@code height} (CSS
     * pixels), drawn by {@code frame} as a window is; {@link ImmediateWindow#place} moves or sizes it. It is native, over
     * the page: the page cannot draw over it (its own menus and tool tips go under it). It closes with its window.
     * Windows only, for now.
     */
    ImmediateWindow embed(View host, double x, double y, double width, double height, Consumer<Ui> frame);

    /** {@link #open(Options, Consumer)} with the system's frame. */
    default ImmediateWindow open(String title, double width, double height, Consumer<Ui> frame) {
        return open(Options.of(title, width, height), frame);
    }
}
