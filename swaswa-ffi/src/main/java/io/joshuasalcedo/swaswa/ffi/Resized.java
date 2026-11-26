package io.joshuasalcedo.swaswa.ffi;

/**
 * A view's window has a new inner size, in logical pixels. While the user drags its edge, at
 * most one per round of the event loop: the last size.
 */
public record Resized(String view, double width, double height) implements WebViewEvent {}
