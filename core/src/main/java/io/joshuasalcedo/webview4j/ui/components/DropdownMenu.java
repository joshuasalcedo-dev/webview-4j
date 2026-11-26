package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A button that opens a menu under it: the same menu and items as a right-click's ({@link ContextMenu}), with
 * icons, shortcuts, ticks and lines between groups.
 * <pre>{@code
 * dropdownMenu("Sort").icon("list")
 *         .item("Name", () -> sort = NAME)
 *         .item("Date", () -> sort = DATE)
 *         .separator()
 *         .check("Newest first", newestFirst, on -> newestFirst = on)
 * }</pre>
 */
public final class DropdownMenu extends Component<DropdownMenu> {

    private final List<Object> entries = new ArrayList<>();

    public DropdownMenu(String label) {
        super("dropdownMenu");
        prop("label", Objects.requireNonNull(label, "label"));
        prop("entries", entries);
    }

    /** An icon by name before the label. */
    public DropdownMenu icon(String icon) { return prop("icon", icon); }
    /** One of Lucide's icons on it. */
    public DropdownMenu icon(io.joshuasalcedo.webview4j.ui.icons.Icon icon) { return prop("icon", icon == null ? null : icon.key()); }
    /** No fill until hovered: toolbars. */
    public DropdownMenu subtle() { return prop("variant", "subtle"); }
    /** The window's main action. */
    public DropdownMenu accent() { return prop("variant", "accent"); }
    public DropdownMenu disabled(boolean disabled) { return prop("disabled", disabled); }

    public DropdownMenu item(String label, Runnable action) {
        return item(ContextMenu.menuItem(label, action));
    }

    public DropdownMenu item(String label, String shortcut, Runnable action) {
        return item(ContextMenu.menuItem(label, action).shortcut(shortcut));
    }

    /** An item with an icon, a shortcut, a tick, or greyed out: {@link ContextMenu#menuItem}. */
    public DropdownMenu item(ContextMenu.Item item) {
        String handler = "onItem" + entries.size();
        entries.add(ContextMenu.entry(item, handler));
        Runnable action = ContextMenu.action(item);
        return on(handler, v -> action.run());
    }

    /** A checkbox item: ticked when {@code checked}; choosing it gives the other state. */
    public DropdownMenu check(String label, boolean checked, Consumer<Boolean> action) {
        Objects.requireNonNull(action, "action");
        return item(ContextMenu.menuItem(label, () -> action.accept(!checked)).checked(checked));
    }

    /** A line between groups of items. */
    public DropdownMenu separator() {
        entries.add("separator");
        return self();
    }
}
