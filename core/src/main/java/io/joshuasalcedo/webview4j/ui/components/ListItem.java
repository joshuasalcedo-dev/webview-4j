package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** One item of a list: a title, a quieter line, something before and after it, a right-click menu. */
public final class ListItem extends Component<ListItem> {

    public ListItem(String title) {
        super("listItem");
        prop("title", title);
    }

    public ListItem subtitle(String subtitle) { return prop("subtitle", subtitle); }
    /** After the text: a time, a count. */
    public ListItem trailing(String trailing) { return prop("trailing", trailing); }
    /** Before the text: an avatar, usually. */
    public ListItem leading(Component<?> leading) { return add(slot(leading, "leading")); }
    public ListItem selected(boolean selected) { return prop("selected", selected); }
    public ListItem onSelect(Runnable action) { return on("onSelect", v -> action.run()); }
    /** Opened: a double-click, or Enter. */
    public ListItem onDoubleClick(Runnable action) { return on("onDoubleClick", v -> action.run()); }
    /** A right-click menu entry; call again for each. */
    public ListItem menu(String label, Runnable action) {
        return add(slot(new Element("menuItem").set("label", label).handle("onSelect", v -> action.run()), "menu"));
    }
}
