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

    /**
     * What goes in the bar's corner: the left edge on Windows, after the red, yellow and green buttons on a Mac (a
     * back button, a sidebar trigger).
     */
    public TitleBar leading(Component<?>... children) {
        for (Component<?> c : children) add(slot(c, "leading"));
        return self();
    }

    /**
     * The button that folds and opens the sidebar, in the bar's corner: {@code titleBar("Mail").sidebarTrigger(() ->
     * folded = !folded)}, the sidebar given {@code .collapsed(folded)}.
     */
    public TitleBar sidebarTrigger(Runnable toggle) { return leading(new SidebarTrigger(toggle)); }
}
