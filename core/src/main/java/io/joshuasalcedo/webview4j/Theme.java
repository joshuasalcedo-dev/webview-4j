package io.joshuasalcedo.webview4j;

/**
 * Light or dark, for a window's frame and its page: the page's {@code prefers-color-scheme}, which
 * {@code @webview4j/ui} follows (its {@code theme="system"}, the default). {@link #SYSTEM} follows the system's
 * setting, and changes with it.
 */
public enum Theme {
    SYSTEM, LIGHT, DARK;

    String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
