package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;
import java.util.function.Consumer;

/**
 * A side bar of places (the app's sections). Two ways to build it:
 * <ul>
 * <li>the platform's own: {@link SidebarSection}s of {@link SidebarItem}s (Windows 11's navigation pane, macOS'
 *     source list);</li>
 * <li>from parts, as shadcn/ui's sidebar is: a {@link #header header}, {@link SidebarGroup}s of
 *     {@link SidebarMenuButton}s (with icons, counts, places under them), a {@link #footer footer}; it folds to its
 *     icons (Ctrl+B, or a {@link SidebarTrigger}).</li>
 * </ul>
 * <pre>{@code
 * sidebar(
 *         sidebarGroup("Mail",
 *                 sidebarMenuButton("Inbox").icon("mail").badge(12).active(page.equals("Inbox")).onClick(() -> page = "Inbox"),
 *                 sidebarMenuButton("Sent").icon("upload")))
 *         .header(sidebarMenuButton("Acme").description("Mail").icon("layers").size(SidebarMenuButton.Size.LARGE))
 *         .footer(sidebarMenuButton("Settings").icon("settings"))
 *         .collapsed(folded).onCollapsedChange(v -> folded = v)
 * }</pre>
 */
public final class Sidebar extends Component<Sidebar> {

    /** How a sidebar built from parts folds. */
    public enum Collapsible {
        /** To a strip of its icons. */
        ICON,
        /** Off the window. */
        OFFCANVAS,
        /** It doesn't. */
        NONE
    }

    /** How a sidebar built from parts sits in the window. */
    public enum Variant {
        /** Along the window's edge, a line between it and the page. */
        SIDEBAR,
        /** A raised panel with a margin around it. */
        FLOATING,
        /** Flush with the window, the page beside it raised. */
        INSET
    }

    public Sidebar(Component<?>... items) {
        super("sidebar");
        add(items);
    }

    /** On top, not scrolled: the app's name and logo (a large {@link SidebarMenuButton}), a {@link SidebarInput}. */
    public Sidebar header(Component<?>... items) { return add(new Element("sidebarHeader").with(items)); }

    /** At the bottom, not scrolled: the account, Settings. */
    public Sidebar footer(Component<?>... items) { return add(new Element("sidebarFooter").with(items)); }

    /** What screen readers call it ("Sections" if left out). */
    public Sidebar label(String label) { return prop("label", label); }

    /** Folded (to icons, or away; see {@link #collapsible}). */
    public Sidebar collapsed(boolean collapsed) { return prop("collapsed", collapsed); }

    /** Ctrl+B (Cmd+B) or a {@link SidebarTrigger} asked it to fold ({@code true}) or open. */
    public Sidebar onCollapsedChange(Consumer<Boolean> action) { return on("onCollapsedChange", v -> action.accept(Keys.flag(v))); }

    public Sidebar collapsible(Collapsible collapsible) { return prop("collapsible", collapsible.name().toLowerCase()); }

    public Sidebar variant(Variant variant) { return prop("variant", variant.name().toLowerCase()); }

    /** On the window's right. */
    public Sidebar right() { return prop("side", "right"); }
}
