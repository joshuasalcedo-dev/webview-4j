package io.joshuasalcedo.webview4j.ui;

/**
 * A window's content, in Java: what {@link #render()} returns is shown with {@code @webview4j/ui}'s components.
 * Keep the screen's state in its fields; change it in the components' handlers ({@code onClick},
 * {@code onChange}...), and the screen renders again by itself after each one. For a change from elsewhere
 * (a timer, a message), call {@link UiWindow#refresh()}.
 */
@FunctionalInterface
public interface Screen {

    /** What to show now. Called again after each handler: build it from the screen's fields, quickly. */
    Component<?> render();
}
