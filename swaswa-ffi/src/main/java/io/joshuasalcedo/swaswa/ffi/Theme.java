package io.joshuasalcedo.swaswa.ffi;

/**
 * Light or dark, for a window's frame and its page: the page's {@code prefers-color-scheme}. {@link #SYSTEM}
 * follows the system's setting, and changes with it.
 */
public enum Theme {
    SYSTEM, LIGHT, DARK;

    String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
