package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** Its children in a column or a row. */
public final class Box extends Component<Box> {

    public Box(String direction, Component<?>... children) {
        super("box");
        prop("direction", direction);
        add(children);
    }

    /** Space between the children, CSS pixels. */
    public Box gap(double gap) { return prop("gap", gap); }
    /** Space inside the edges, CSS pixels. */
    public Box padding(double padding) { return prop("padding", padding); }
    /** Children that do not fit go on to the next line. */
    public Box wrap() { return prop("wrap", true); }
    /** Lines the children up across: "start", "center", "end" or "stretch". */
    public Box align(String align) { return prop("align", align); }
    /** Scrolls when its children do not fit. */
    public Box scroll() { return prop("scroll", true); }
    public Box with(Component<?>... children) { return add(children); }
    public Box with(java.util.List<? extends Component<?>> children) { return add(children); }
    /** Any property for the renderer, such as an accessibility role. */
    public Box attribute(String name, Object value) { return prop(name, value); }
}
