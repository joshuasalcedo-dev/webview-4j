package io.joshuasalcedo.swaswa.ffi;

/** A view's window was minimized, maximized, made fullscreen, or restored: how it is shown now. */
public record WindowState(String view, boolean minimized, boolean maximized, boolean fullscreen) implements WebViewEvent {

    /** Neither minimized, maximized nor fullscreen. */
    public boolean restored() {
        return !minimized && !maximized && !fullscreen;
    }
}
