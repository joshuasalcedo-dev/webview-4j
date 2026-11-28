package io.joshuasalcedo.swaswa.ffi;

/** The system switched between light and dark (a window following the system, {@link Theme#SYSTEM}, follows). */
public record SystemThemeChanged(String view, Theme theme) implements WebViewEvent {}
