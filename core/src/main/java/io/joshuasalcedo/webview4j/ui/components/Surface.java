package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** A card of related controls under a heading: Windows' card, macOS' group box. */
public final class Surface extends Component<Surface> {

    public Surface(String title, Component<?>... children) {
        super("surface");
        prop("title", title);
        add(children);
    }

    public Surface with(Component<?>... children) { return add(children); }
}
