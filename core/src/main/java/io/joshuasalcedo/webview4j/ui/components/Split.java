package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** A side bar on the left and the content filling the rest. */
public final class Split extends Component<Split> {

    public Split(Sidebar sidebar, Component<?> content) {
        super("split");
        add(sidebar, content);
    }

}
