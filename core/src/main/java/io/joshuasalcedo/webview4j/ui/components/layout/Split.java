package io.joshuasalcedo.webview4j.ui.components.layout;

import io.joshuasalcedo.webview4j.ui.Component;
import io.joshuasalcedo.webview4j.ui.components.Sidebar;

/** A side bar on the left and the content filling the rest. */
public final class Split extends Component<Split> {

    public Split(Sidebar sidebar, Component<?> content) {
        super("split");
        add(sidebar, content);
    }

}
