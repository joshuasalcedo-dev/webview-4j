package io.joshuasalcedo.swaswa.ffi;

/** A view's window moved to a display of another scale (or the display's scale changed): its scale factor now. */
public record ScaleChanged(String view, double scale) implements WebViewEvent {}
