package io.joshuasalcedo.webview4j.ui.components.layout;

import io.joshuasalcedo.webview4j.ui.Component;

/** What every layout pane has: space inside its edge, and where its children sit. */
public abstract class Pane<C extends Pane<C>> extends Component<C> {

    protected Pane(String type) {
        super(type);
    }

    /** The same space inside every edge, CSS pixels. */
    public C padding(double padding) {
        return prop("padding", padding);
    }

    public C padding(Insets padding) {
        return prop("padding", padding.write());
    }

    /** Where the children sit. */
    public C alignment(Pos alignment) {
        return prop("alignment", alignment.key());
    }

    /**
     * Adds children at the end (Swing's {@code panel.add(button)}); a window showing it draws them.
     * <pre>{@code
     * VBox panel = new VBox(12);
     * panel.add(new Text("Name"));
     * panel.add(nameField, saveButton);
     * }</pre>
     */
    @Override
    public C add(Component<?>... children) {
        return super.add(children);
    }

    /** Adds {@code child} at {@code index} (Swing's {@code add(component, index)}). */
    public C add(Component<?> child, int index) {
        return insert(index, child);
    }

    /** Takes {@code child} out; a window showing it draws again. */
    @Override
    public C remove(Component<?> child) {
        return super.remove(child);
    }

    /** Takes every child out (Swing's {@code removeAll}). */
    public C removeAll() {
        return clear();
    }

    /** Children added after the ones given at first (JavaFX's getChildren().addAll). */
    public C addAll(Component<?>... children) {
        for (Component<?> child : children) add(child);
        return self();
    }

    public C addAll(java.util.List<? extends Component<?>> children) {
        for (Component<?> child : children) add(child);
        return self();
    }
}
