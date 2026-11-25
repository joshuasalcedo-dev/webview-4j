package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/**
 * One place in a {@link Sidebar} built from parts: an icon, its name, maybe a count; with {@link #items} it opens to
 * the places under it.
 */
public final class SidebarMenuButton extends Component<SidebarMenuButton> {

    public enum Size {
        SMALL("sm"),
        DEFAULT("default"),
        /** Taller, its icon a tile, with a {@link #description}: the app's name in the header. */
        LARGE("lg");

        final String css;

        Size(String css) {
            this.css = css;
        }
    }

    public SidebarMenuButton(String label) {
        super("sidebarMenuButton");
        prop("label", label);
    }

    /**
     * Its icon by name: home, search, settings, cursor, form, text, layout, columns, file, folder, code, table, list,
     * chart, money, windows, window, lock, user, users, bell, mail, chat, image, palette, package, layers, grid,
     * sparkles, star, bookmark, calendar, clock, globe, download, upload, plus, check, info, help, logout, terminal,
     * play, video, bolt, gauge, heart, tag, shield, key, book, more. One or two characters (an emoji, initials) are
     * drawn as they are.
     */
    public SidebarMenuButton icon(String icon) { return prop("icon", icon); }

    /** It is the page shown now. */
    public SidebarMenuButton active(boolean active) { return prop("active", active); }

    /** A count at its end (0 shows nothing). */
    public SidebarMenuButton badge(long badge) { return badge == 0 ? self() : prop("badge", badge); }

    /** A short text at its end ("New", "Beta"). */
    public SidebarMenuButton badge(String badge) { return prop("badge", badge); }

    /** A second, quieter line (with {@link Size#LARGE}). */
    public SidebarMenuButton description(String description) { return prop("description", description); }

    public SidebarMenuButton size(Size size) { return prop("size", size.css); }

    /** Shown on hover when the sidebar is folded to icons (its label if left out). */
    public SidebarMenuButton tooltip(String tooltip) { return prop("tooltip", tooltip); }

    public SidebarMenuButton disabled(boolean disabled) { return prop("disabled", disabled); }

    public SidebarMenuButton onClick(Runnable action) { return on("onClick", v -> action.run()); }

    /** The places under it, shown when it opens (it opens by itself when one of them is {@link #active}). */
    public SidebarMenuButton items(SidebarMenuButton... items) { return add(items); }

    /** Open at first (with {@link #items}). */
    public SidebarMenuButton open(boolean open) { return prop("open", open); }
}
