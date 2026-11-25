package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A desktop app's menus across the top (File, Edit, View...), each opening the same menu a right-click opens:
 * items with icons and shortcuts, ticks, lines between groups.
 * <pre>{@code
 * menubar()
 *         .menu("File", m -> m.item("New", "Mod+N", this::newFile).item("Open…", "Mod+O", this::open).separator().item("Quit", app::quit))
 *         .menu("View", m -> m.check("Side bar", sideBar, on -> sideBar = on))
 * }</pre>
 */
public final class Menubar extends Component<Menubar> {

    /** One menu's items, as {@link #menu} is given them. */
    public final class Menu {
        private final List<Object> entries = new ArrayList<>();
        private final int index;

        private Menu(int index) {
            this.index = index;
        }

        public Menu item(String label, Runnable action) { return item(ContextMenu.menuItem(label, action)); }
        public Menu item(String label, String shortcut, Runnable action) { return item(ContextMenu.menuItem(label, action).shortcut(shortcut)); }

        /** An item with an icon, a shortcut, a tick, or greyed out: {@link ContextMenu#menuItem}. */
        public Menu item(ContextMenu.Item item) {
            String handler = "onMenu" + index + "Item" + entries.size();
            entries.add(ContextMenu.entry(item, handler));
            Runnable action = ContextMenu.action(item);
            on(handler, v -> action.run());
            return this;
        }

        /** A checkbox item: ticked when {@code checked}; choosing it gives the other state. */
        public Menu check(String label, boolean checked, Consumer<Boolean> action) {
            Objects.requireNonNull(action, "action");
            return item(ContextMenu.menuItem(label, () -> action.accept(!checked)).checked(checked));
        }

        public Menu separator() {
            entries.add("separator");
            return this;
        }
    }

    private final List<Map<String, Object>> menus = new ArrayList<>();

    public Menubar() {
        super("menubar");
        prop("menus", menus);
    }

    /** A menu titled {@code label}, its items added by {@code items}. */
    public Menubar menu(String label, Consumer<Menu> items) {
        Menu menu = new Menu(menus.size());
        Objects.requireNonNull(items, "items").accept(menu);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", Objects.requireNonNull(label, "label"));
        m.put("entries", menu.entries);
        menus.add(m);
        return self();
    }
}
