package io.joshuasalcedo.webview4j.ui.components.layout;

import io.joshuasalcedo.webview4j.ui.Component;

/**
 * Children side by side, left to right, as JavaFX's {@code HBox}.
 * <pre>{@code
 * new HBox(8, new Button("Back"), HBox.hgrow(new Text("Title")), new Button("Next")).alignment(Pos.CENTER_LEFT)
 * }</pre>
 */
public final class HBox extends Pane<HBox> {

    public HBox(Component<?>... children) {
        super("hbox");
        addAll(children);
    }

    /** With {@code spacing} CSS pixels between the children. */
    public HBox(double spacing, Component<?>... children) {
        this(children);
        spacing(spacing);
    }

    public HBox spacing(double spacing) {
        return prop("spacing", spacing);
    }

    /** Children as tall as the HBox (on by default, as JavaFX's fillHeight). */
    public HBox fillHeight(boolean fill) {
        return prop("fill", fill);
    }

    /** Onto the next line when there is no room, as JavaFX's FlowPane. */
    public HBox wrap() {
        return prop("wrap", true);
    }

    /** Scrolls when the children do not fit. */
    public HBox scroll() {
        return prop("scroll", true);
    }

    /** A child that takes the width left over (JavaFX's {@code HBox.setHgrow(node, Priority.ALWAYS)}). */
    public static <T extends Component<?>> T hgrow(T child) {
        child.grow();
        return child;
    }

    /** As JavaFX writes it. */
    public static void setHgrow(Component<?> child, Priority priority) {
        if (priority == Priority.ALWAYS) child.grow();
    }
}
