package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** The bar along the window's bottom: what the app is doing, counts. */
public final class StatusBar extends Component<StatusBar> {

    public StatusBar(Component<?>... items) {
        super("statusBar");
        add(items);
    }

}
