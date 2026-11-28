package io.joshuasalcedo.swaswa.ffi;

/** The page's {@code document.title} changed, for a tab or a window's title. */
public record TitleChanged(String view, String title) implements WebViewEvent {}
