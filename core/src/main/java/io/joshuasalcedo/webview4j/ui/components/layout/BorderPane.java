package io.joshuasalcedo.webview4j.ui.components.layout;

import io.joshuasalcedo.webview4j.ui.Component;

/**
 * A center with what goes around it, as JavaFX's {@code BorderPane}: top, left, right and bottom take the room they
 * need; the center takes what is left.
 * <pre>{@code
 * new BorderPane(editor).top(toolbar).left(outline).bottom(statusLine)
 * }</pre>
 */
public final class BorderPane extends Pane<BorderPane> {

    public BorderPane() {
        super("borderpane");
    }

    public BorderPane(Component<?> center) {
        this();
        center(center);
    }

    public BorderPane top(Component<?> top) { return place(top, "top"); }
    public BorderPane left(Component<?> left) { return place(left, "left"); }
    public BorderPane center(Component<?> center) { return place(center, "center"); }
    public BorderPane right(Component<?> right) { return place(right, "right"); }
    public BorderPane bottom(Component<?> bottom) { return place(bottom, "bottom"); }

    /** Space between the center and what is around it. */
    public BorderPane spacing(double spacing) {
        return prop("spacing", spacing);
    }

    private BorderPane place(Component<?> child, String area) {
        children().removeIf(c -> area.equals(slotOf(c)));
        return add(slot(child, area));
    }
}
