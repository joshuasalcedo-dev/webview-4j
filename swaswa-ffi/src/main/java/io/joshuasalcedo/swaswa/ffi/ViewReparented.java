package io.joshuasalcedo.swaswa.ffi;

/**
 * A view moved to another window, its page running on: into {@code host}'s window ({@link View#adopt}), or out into a
 * window of its own ({@link View#detach}, {@code host} null). Same view, same id, same page.
 */
public record ViewReparented(String view, String host) implements WebViewEvent {}
