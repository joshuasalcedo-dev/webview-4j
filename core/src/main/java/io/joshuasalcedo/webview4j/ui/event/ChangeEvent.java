package io.joshuasalcedo.webview4j.ui.event;

import io.joshuasalcedo.webview4j.ui.Component;
import java.util.Objects;

/**
 * A component's value changed because the user changed it: text typed, a box checked, a choice picked, a slider
 * moved (Swing's {@code ChangeEvent}, with the new value in it).
 *
 * @param <V> the value's type: {@code String} for text, {@code Boolean} for a check box, {@code Double} for a slider
 */
public final class ChangeEvent<V> {

    private final Component<?> source;
    private final V value;

    public ChangeEvent(Component<?> source, V value) {
        this.source = Objects.requireNonNull(source, "source");
        this.value = value;
    }

    /** The component that changed. */
    public Component<?> getSource() {
        return source;
    }

    /** Its value now; the component already holds it too ({@code field.getText()}). */
    public V getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "ChangeEvent[" + source.getClass().getSimpleName() + ": " + value + "]";
    }
}
