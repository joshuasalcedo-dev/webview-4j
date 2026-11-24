package io.joshuasalcedo.webview4j.ui.components.layout;

import io.joshuasalcedo.webview4j.ui.Component;

/**
 * Children one under the other, top to bottom, as JavaFX's {@code VBox}.
 * <pre>{@code
 * new VBox(12, title, VBox.vgrow(table), buttons).padding(16)
 * }</pre>
 */
public final class VBox extends Pane<VBox> {

    public VBox(Component<?>... children) {
        super("vbox");
        addAll(children);
    }

    /** With {@code spacing} CSS pixels between the children. */
    public VBox(double spacing, Component<?>... children) {
        this(children);
        spacing(spacing);
    }

    public VBox spacing(double spacing) {
        return prop("spacing", spacing);
    }

    /** Children as wide as the VBox (on by default, as JavaFX's fillWidth). */
    public VBox fillWidth(boolean fill) {
        return prop("fill", fill);
    }

    /** Scrolls when the children do not fit. */
    public VBox scroll() {
        return prop("scroll", true);
    }

    /** A child that takes the height left over (JavaFX's {@code VBox.setVgrow(node, Priority.ALWAYS)}). */
    public static <T extends Component<?>> T vgrow(T child) {
        child.grow();
        return child;
    }

    /** As JavaFX writes it. */
    public static void setVgrow(Component<?> child, Priority priority) {
        if (priority == Priority.ALWAYS) child.grow();
    }
}
