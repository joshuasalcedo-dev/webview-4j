package io.joshuasalcedo.swaswa.ffi;

/**
 * A page asked for a new window ({@code target=_blank}, {@code window.open}) in a window opened with
 * {@link WindowOptions#newWindows(WindowOptions.NewWindows) newWindows(JAVA)}: it was refused, for Java to decide
 * (open it in a window of the app's own, in the system's browser, or not).
 */
public record NewWindowRequested(String view, String url) implements WebViewEvent {}
