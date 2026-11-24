package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** A person's picture, or their initials on the accent colour. */
public final class Avatar extends Component<Avatar> {

    public Avatar(String name) {
        super("avatar");
        prop("name", name);
    }

    public Avatar src(String src) { return prop("src", src); }
    public Avatar size(double size) { return prop("size", size); }
}
