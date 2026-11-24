package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** A keyboard shortcut, written as the system writes it: "Mod+K" is ⌘K on a Mac and Ctrl+K on Windows. */
public final class Shortcut extends Component<Shortcut> {

    public Shortcut(String keys) {
        super("shortcut");
        prop("keys", keys);
    }

}
