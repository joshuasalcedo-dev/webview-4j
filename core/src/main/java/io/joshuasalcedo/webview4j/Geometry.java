package io.joshuasalcedo.webview4j;

/**
 * Where a view's window is and how it is shown, from {@link View#geometry()}: its outer position
 * and inner size in logical pixels, and the display's scale factor (physical pixels per logical
 * one). Enough to save a window's place and open it there next time.
 */
public record Geometry(double x, double y, double width, double height, double scaleFactor,
                       boolean minimized, boolean maximized, boolean fullscreen, boolean focused, boolean visible) {}
