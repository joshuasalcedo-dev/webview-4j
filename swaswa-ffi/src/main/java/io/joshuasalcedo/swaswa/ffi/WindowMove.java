package io.joshuasalcedo.swaswa.ffi;

/**
 * A person moving or sizing a window (its title bar or an edge dragged), or an MDI child inside its window: when they
 * start, while they do (about 30 times a second), and when they let go. Its outer bounds then, in physical pixels; the
 * pointer's place (physical); and which of Ctrl, Shift and Alt are held (a key that says "keep it floating"). At
 * {@link Phase#ENDED}, {@link App#windowAt(double, double, View)} with the pointer says what it was let go over;
 * {@link Docking} does the rest.
 */
public record WindowMove(String view, Phase phase, int x, int y, int width, int height, int cursorX, int cursorY,
                         boolean ctrl, boolean shift, boolean alt) implements WebViewEvent {

    public enum Phase { STARTED, MOVING, ENDED }
}
