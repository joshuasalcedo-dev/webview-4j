package io.joshuasalcedo.webview4j.ui.event;

import io.joshuasalcedo.webview4j.ui.Component;
import java.util.Objects;

/**
 * The user acted on a component: a button pressed, Enter in a text field, a menu item chosen (Swing's
 * {@code java.awt.event.ActionEvent}).
 */
public final class ActionEvent {

    private final Component<?> source;

    public ActionEvent(Component<?> source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    /** The component acted on. */
    public Component<?> getSource() {
        return source;
    }

    @Override
    public String toString() {
        return "ActionEvent[" + source.getClass().getSimpleName() + "]";
    }
}
