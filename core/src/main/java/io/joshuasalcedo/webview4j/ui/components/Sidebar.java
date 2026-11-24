package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** A side bar of places (the app's sections). */
public final class Sidebar extends Component<Sidebar> {

    public Sidebar(Component<?>... items) {
        super("sidebar");
        add(items);
    }

    /** Items at the bottom, such as Settings. */
    public Sidebar footer(Component<?>... items) { return add(new Element("sidebarFooter").with(items)); }
}
