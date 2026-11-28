package io.joshuasalcedo.swaswa.ffi;

/**
 * A view's window has moved: its outer position, in logical pixels. While the user drags it, at
 * most one per round of the event loop: the last position.
 */
public record Moved(String view, double x, double y) implements WebViewEvent {}
