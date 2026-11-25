package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** A push button, as the system draws one. */
public final class Button extends Component<Button> {

    public Button(String label) {
        super("button");
        prop("label", label);
    }

    /** Says this now; a window showing it draws again. */
    public Button label(String label) { return prop("label", label); }
    /** The window's main action: one per window. */
    public Button accent() { return prop("variant", "accent"); }
    /** No fill until hovered: toolbars, icon buttons. */
    public Button subtle() { return prop("variant", "subtle"); }
    public Button disabled(boolean disabled) { return prop("disabled", disabled); }
    public Button onClick(Runnable action) { return on("onClick", v -> action.run()); }
}
