package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** A group of side bar items under a heading (or none). */
public final class SidebarSection extends Component<SidebarSection> {

    public SidebarSection(String title, Component<?>... items) {
        super("sidebarSection");
        prop("title", title);
        add(items);
    }

}
