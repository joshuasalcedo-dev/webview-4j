package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** A part of a {@link Sidebar} under a small heading: its {@link SidebarMenuButton}s, or anything else. */
public final class SidebarGroup extends Component<SidebarGroup> {

    public SidebarGroup(String label, Component<?>... items) {
        super("sidebarGroup");
        if (label != null) prop("label", label);
        add(items);
    }

    /** Its heading folds and unfolds it. */
    public SidebarGroup collapsible(boolean collapsible) { return prop("collapsible", collapsible); }

    /** Folded or not at first, when {@link #collapsible} (open by default). */
    public SidebarGroup open(boolean open) { return prop("open", open); }
}
