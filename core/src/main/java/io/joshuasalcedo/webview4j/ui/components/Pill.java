package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** A small coloured label: a status, a tag. */
public final class Pill extends Component<Pill> {

    public Pill(String text) {
        super("pill");
        prop("text", text);
    }

    public Pill color(PillColor color) { return prop("color", color.key()); }
    public Pill filled() { return prop("variant", "filled"); }
    public Pill outline() { return prop("variant", "outline"); }
}
