package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** One place in the side bar. */
public final class SidebarItem extends Component<SidebarItem> {

    public SidebarItem(String label) {
        super("sidebarItem");
        prop("label", label);
    }

    public SidebarItem selected(boolean selected) { return prop("selected", selected); }
    /** A count after the label, such as unread messages. */
    public SidebarItem badge(long badge) { return prop("badge", badge); }
    public SidebarItem onSelect(Runnable action) { return on("onSelect", v -> action.run()); }
}
