package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** One piece of the status bar. */
public final class StatusItem extends Component<StatusItem> {

    public StatusItem(String text) {
        super("statusItem");
        prop("text", text);
    }

    /** A coloured light before it. */
    public StatusItem dot(DotState state) { return prop("dot", state.key()); }
}
