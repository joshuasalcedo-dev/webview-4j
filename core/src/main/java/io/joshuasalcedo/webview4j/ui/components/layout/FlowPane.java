package io.joshuasalcedo.webview4j.ui.components.layout;

import io.joshuasalcedo.webview4j.ui.Component;

/**
 * Children one after the other, onto the next line when there is no room, as JavaFX's {@code FlowPane} (Swing's
 * {@code FlowLayout}): tags, chips, buttons.
 * <pre>{@code
 * new FlowPane(6, 6, tags.stream().map(t -> pill(t)).toArray(Component[]::new))
 * }</pre>
 */
public final class FlowPane extends Pane<FlowPane> {

    /** Lines left to right, or columns top to bottom. */
    public enum Orientation { HORIZONTAL, VERTICAL }

    public FlowPane(Component<?>... children) {
        super("flowpane");
        addAll(children);
    }

    public FlowPane(double hgap, double vgap, Component<?>... children) {
        this(children);
        hgap(hgap);
        vgap(vgap);
    }

    public FlowPane hgap(double gap) { return prop("hgap", gap); }
    public FlowPane vgap(double gap) { return prop("vgap", gap); }

    public FlowPane orientation(Orientation orientation) {
        return prop("orientation", orientation == Orientation.VERTICAL ? "vertical" : "horizontal");
    }
}
