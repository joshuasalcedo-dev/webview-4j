package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** A small round light for a connection's state. */
public final class StatusDot extends Component<StatusDot> {

    public StatusDot(DotState state) {
        super("statusDot");
        prop("state", state.key());
    }

}
