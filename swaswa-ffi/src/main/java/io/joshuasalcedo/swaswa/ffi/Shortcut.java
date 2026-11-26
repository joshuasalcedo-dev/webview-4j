package io.joshuasalcedo.swaswa.ffi;

/**
 * A key the window answers itself was pressed in its page ({@link WindowOptions#shortcuts},
 * {@link View#setShortcuts}): {@code Mod+S}. The page did nothing with it.
 */
public record Shortcut(String view, String shortcut) implements WebViewEvent {}
