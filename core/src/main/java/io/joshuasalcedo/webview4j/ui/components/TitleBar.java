package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** The window's own title bar, for a window without the system's frame: it moves the window, and its buttons minimize, maximize and close it. */
public final class TitleBar extends Component<TitleBar> {

    public TitleBar(String title) {
        super("titleBar");
        prop("title", title);
    }

    /** What goes in the bar's free space: a search field, buttons. */
    public TitleBar with(Component<?>... children) { return add(children); }
}
