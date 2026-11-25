package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * The app's commands, found by typing part of a name and run with Enter: opened with Ctrl+K (⌘K on a Mac) from
 * anywhere in the window, as in VS Code, Linear or Slack. Commands come in groups, with an icon and a shortcut.
 * <pre>{@code
 * commandPalette(paletteOpen).onOpenChange(open -> paletteOpen = open)
 *         .command("Files", "New file", "Mod+N", this::newFile)
 *         .command("Files", "Open…", "Mod+O", this::open)
 *         .command("View", "Toggle side bar", "Mod+B", () -> sideBar = !sideBar)
 * }</pre>
 */
public final class CommandPalette extends Component<CommandPalette> {

    private final List<Map<String, Object>> commands = new ArrayList<>();

    /** Open or closed: keep it in a field and give {@link #onOpenChange} what the person did. */
    public CommandPalette(boolean open) {
        super("commandPalette");
        prop("open", open);
        prop("commands", commands);
    }

    /** It opened (its shortcut) or closed (Esc, a click outside, a command run). */
    public CommandPalette onOpenChange(Consumer<Boolean> action) {
        return on("onOpenChange", v -> action.accept(Keys.flag(v)));
    }

    /** The keys that open it: {@code "Mod+K"} unless set; {@code ""} for none (open it from Java). */
    public CommandPalette shortcut(String keys) { return prop("shortcut", keys); }
    public CommandPalette placeholder(String placeholder) { return prop("placeholder", placeholder); }
    /** What it says when nothing matches. */
    public CommandPalette emptyText(String text) { return prop("emptyText", text); }

    public CommandPalette command(String label, Runnable action) { return command(null, label, null, null, action); }
    public CommandPalette command(String group, String label, Runnable action) { return command(group, label, null, null, action); }
    public CommandPalette command(String group, String label, String shortcut, Runnable action) { return command(group, label, shortcut, null, action); }

    /**
     * A command: its group's name (commands of a group stay together, in the order added), what it is called, a
     * shortcut shown beside it (only shown), an icon by name, and what it runs.
     */
    public CommandPalette command(String group, String label, String shortcut, String icon, Runnable action) {
        Objects.requireNonNull(action, "action");
        String handler = "onCommand" + commands.size();
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("id", handler);
        c.put("label", Objects.requireNonNull(label, "label"));
        if (group != null) c.put("group", group);
        if (shortcut != null) c.put("shortcut", shortcut);
        if (icon != null) c.put("icon", icon);
        commands.add(c);
        return on(handler, v -> action.run());
    }
}
