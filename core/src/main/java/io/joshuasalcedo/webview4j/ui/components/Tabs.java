package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** Tabs across the top of what they switch between (the content is yours to show for the chosen one). */
public final class Tabs extends Component<Tabs> {

    public Tabs(String label) {
        super("tabs");
        prop("label", label);
    }

    public Tabs tab(String value, String label) { return tab(value, label, false); }

    /** A tab; {@code closable} gives it a close button ({@link #onClose}). */
    public Tabs tab(String value, String label, boolean closable) {
        return add(new Element("tab").set("value", value).set("label", label).set("closable", closable));
    }

    public Tabs value(String value) { return prop("value", value); }
    public Tabs onChange(java.util.function.Consumer<String> action) { return on("onChange", v -> action.accept(Keys.text(v))); }
    public Tabs onClose(java.util.function.Consumer<String> action) { return on("onClose", v -> action.accept(Keys.text(v))); }
    /** A + after the tabs. */
    public Tabs onAdd(Runnable action) { return on("onAdd", v -> action.run()); }
}
