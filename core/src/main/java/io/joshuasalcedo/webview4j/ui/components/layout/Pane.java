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
