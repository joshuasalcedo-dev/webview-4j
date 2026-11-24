package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** A list of items, as the system draws one. */
public final class ListView extends Component<ListView> {

    public ListView(String label, Component<?>... items) {
        super("list");
        prop("label", label);
        add(items);
    }

    public ListView with(Component<?>... items) { return add(items); }
    public ListView with(java.util.List<? extends Component<?>> items) { return add(items); }
}
