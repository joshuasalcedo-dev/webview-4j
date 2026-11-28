package io.joshuasalcedo.swaswa.ffi;

/** A view's window closed, by the user or by {@link View#close()}. */
public record Closed(String view) implements WebViewEvent {}
