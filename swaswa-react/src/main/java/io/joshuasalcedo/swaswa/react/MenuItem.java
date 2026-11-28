package io.joshuasalcedo.swaswa.react;

import java.util.Objects;

/**
 * One choice in a component's menu (a table row's, a file's, a message's right-click menu), or a line between groups
 * of them. Chosen, its action runs in Java.
 * <pre>{@code
 * table.setRowMenu(Order.class, order -> List.of(
 *         MenuItem.of("Open", () -> open(order)),
 *         MenuItem.separator(),
 *         MenuItem.of("Delete", () -> delete(order)).withShortcut("Delete")));
 * }</pre>
 *
 * @param label    what it says
 * @param shortcut its keys as written for code ({@code "Mod+C"}: ⌘C on a Mac, Ctrl+C on Windows); only shown
 * @param checked  a tick before it (a checkbox item); null for none
 * @param disabled shown, not chosen
 * @param action   what choosing it does
 */
public record MenuItem(String label, String shortcut, Boolean checked, boolean disabled, Runnable action) {

    private static final MenuItem SEPARATOR = new MenuItem("", null, null, false, null);

    public static MenuItem of(String label, Runnable action) {
        return new MenuItem(Objects.requireNonNull(label, "label"), null, null, false, action);
    }

    /** A line between groups. */
    public static MenuItem separator() {
        return SEPARATOR;
    }

    public boolean isSeparator() {
        return this == SEPARATOR;
    }

    public MenuItem withShortcut(String keys) {
        return new MenuItem(label, keys, checked, disabled, action);
    }

    public MenuItem withChecked(boolean on) {
        return new MenuItem(label, shortcut, on, disabled, action);
    }

    public MenuItem withDisabled(boolean off) {
        return new MenuItem(label, shortcut, checked, off, action);
    }
}
