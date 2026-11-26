package io.joshuasalcedo.webview4j.ui.event;

/** What runs when the user changes a component's value (Swing's {@code ChangeListener}): {@code field.addChangeListener(e -> search(e.getValue()))}. */
@FunctionalInterface
public interface ChangeListener<V> {

    void stateChanged(ChangeEvent<V> e);
}
