package io.joshuasalcedo.swaswa.ffi;

/**
 * The user asked to close a view's window (its close button, Alt+F4, the dock). Only for windows
 * opened with {@link WindowOptions#confirmClose(boolean) confirmClose(true)}: the window stays open
 * until the app calls {@link View#close()}, so it can ask about unsaved work first.
 * <pre>{@code
 * view.subscribe(CloseRequested.class, request -> {
 *     if (app.confirm("Quit?", "Unsaved changes will be lost.").join()) view.close();
 * });
 * }</pre>
 */
public record CloseRequested(String view) implements WebViewEvent {}
