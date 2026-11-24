package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** A command in a toolbar. */
public final class ToolbarButton extends Component<ToolbarButton> {

    public ToolbarButton(String label) {
        super("toolbarButton");
        prop("label", label);
    }

    /** A toggle: on is shown pressed. */
    public ToolbarButton pressed(boolean pressed) { return prop("pressed", pressed); }
    public ToolbarButton disabled(boolean disabled) { return prop("disabled", disabled); }
    public ToolbarButton onClick(Runnable action) { return on("onClick", v -> action.run()); }
}
