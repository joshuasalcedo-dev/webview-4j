package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** A panel at the side with the details of what is chosen; it can be resized and closed. */
public final class Inspector extends Component<Inspector> {

    public Inspector(String title, Component<?>... children) {
        super("inspector");
        prop("title", title);
        add(children);
    }

    public Inspector open(boolean open) { return prop("open", open); }
    public Inspector onOpenChange(java.util.function.Consumer<Boolean> action) { return on("onOpenChange", v -> action.accept(Keys.flag(v))); }
    public Inspector panelWidth(double width) { return prop("defaultWidth", width); }
    public Inspector with(Component<?>... children) { return add(children); }
}
