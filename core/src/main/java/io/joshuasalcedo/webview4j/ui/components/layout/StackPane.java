package io.joshuasalcedo.webview4j.ui.components.layout;

import io.joshuasalcedo.webview4j.ui.Component;
import io.joshuasalcedo.webview4j.ui.components.Element;

/**
 * Children on top of each other, the last on top, as JavaFX's {@code StackPane}: a badge over a picture, a spinner
 * over a form.
 * <pre>{@code
 * new StackPane(picture).add(pill("3"), Pos.TOP_RIGHT, new Insets(6))
 * }</pre>
 */
public final class StackPane extends Pane<StackPane> {

    public StackPane(Component<?>... children) {
        super("stackpane");
        addAll(children);
    }

    /** A child that sits somewhere of its own (JavaFX's {@code StackPane.setAlignment(node, pos)}). */
    public StackPane add(Component<?> child, Pos alignment) {
        return add(child, alignment, Insets.EMPTY);
    }

    /** ... with space around it (JavaFX's {@code StackPane.setMargin}). */
    public StackPane add(Component<?> child, Pos alignment, Insets margin) {
        return add(new Element("stackItem").set("alignment", alignment.key()).set("margin", margin.write()).with(child));
    }
}
