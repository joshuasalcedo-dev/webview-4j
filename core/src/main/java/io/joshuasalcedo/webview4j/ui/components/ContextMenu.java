package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * The menu a right-click opens on a part of the screen (also Shift+F10 or the menu key when it has the focus), as
 * the system draws one: items that run Java code, with an icon, a shortcut shown the system's way ({@code "Mod+C"}
 * is Ctrl+C on Windows, ⌘C on a Mac), a tick, or greyed out; and lines between groups of them.
 * <pre>{@code
 * contextMenu(listItem(file.name()))
 *         .item("Open", () -> open(file))
 *         .item(ContextMenu.menuItem("Copy", () -> copy(file)).shortcut("Mod+C").icon("download"))
 *         .separator()
 *         .check("Pinned", file.pinned(), on -> pin(file, on))
 *         .item(ContextMenu.menuItem("Delete", () -> delete(file)).disabled(!file.deletable()))
 * }</pre>
 */
public final class ContextMenu extends Component<ContextMenu> {

    /** One item: what it says, and what it runs. Made with {@link ContextMenu#menuItem(String, Runnable)}. */
    public static final class Item {
        private final String label;
        private final Runnable action;
        private String shortcut;
        private String icon;
        private Boolean checked;
        private boolean disabled;

        private Item(String label, Runnable action) {
            this.label = Objects.requireNonNull(label, "label");
            this.action = Objects.requireNonNull(action, "action");
        }

        /** A shortcut shown beside it, as written for code: {@code "Mod+C"}, {@code "Shift+Delete"}. Only shown, not bound. */
        public Item shortcut(String shortcut) { this.shortcut = shortcut; return this; }
        /** An icon by name ({@code "download"}, {@code "trash"}...), or one or two characters (an emoji). */
        public Item icon(String icon) { this.icon = icon; return this; }
        /** A tick before it: a checkbox item. */
        public Item checked(boolean checked) { this.checked = checked; return this; }
        /** Greyed out: it shows, it cannot be chosen. */
        public Item disabled(boolean disabled) { this.disabled = disabled; return this; }
    }

    /** An item to add with {@link #item(Item)}, to give it a shortcut, an icon, a tick or grey it out. */
    public static Item menuItem(String label, Runnable action) {
        return new Item(label, action);
    }

    private final List<Object> entries = new ArrayList<>();

    /** The menu for {@code target}: right-clicking anywhere on it opens it. */
    public ContextMenu(Component<?> target) {
        super("contextMenu");
        add(Objects.requireNonNull(target, "target"));
        prop("entries", entries);
    }

    public ContextMenu item(String label, Runnable action) {
        return item(menuItem(label, action));
    }

    public ContextMenu item(String label, String shortcut, Runnable action) {
        return item(menuItem(label, action).shortcut(shortcut));
    }

    public ContextMenu item(Item item) {
        String handler = "onItem" + entries.size();
        entries.add(entry(item, handler));
        Runnable action = item.action;
        return on(handler, v -> action.run());
    }

    /** An item as the page's menu reads it; its Java code is the handler named {@code handler}. */
    static Map<String, Object> entry(Item item, String handler) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("label", item.label);
        entry.put("handler", handler);
        if (item.shortcut != null) entry.put("shortcut", item.shortcut);
        if (item.icon != null) entry.put("icon", item.icon);
        if (item.checked != null) entry.put("checked", item.checked);
        if (item.disabled) entry.put("disabled", true);
        return entry;
    }

    /** What an item runs. */
    static Runnable action(Item item) {
        return item.action;
    }

    /** A checkbox item: ticked when {@code checked}; choosing it gives the other state. */
    public ContextMenu check(String label, boolean checked, Consumer<Boolean> action) {
        Objects.requireNonNull(action, "action");
        return item(menuItem(label, () -> action.accept(!checked)).checked(checked));
    }

    /** A line between groups of items. */
    public ContextMenu separator() {
        entries.add("separator");
        return self();
    }
}
