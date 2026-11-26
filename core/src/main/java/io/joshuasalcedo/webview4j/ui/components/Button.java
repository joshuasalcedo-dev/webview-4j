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
    /** One of Lucide's icons before its label. */
    public Button icon(io.joshuasalcedo.webview4j.ui.icons.Icon icon) { return prop("icon", icon == null ? null : icon.key()); }

    // --- as Swing writes it ---

    public String getText() { return get("label") instanceof String s ? s : ""; }
    /** Says {@code text} now; a window showing it draws again. */
    public Button setText(String text) { return label(text); }
    /** Runs when it is pressed; as many as are added, in order. */
    public Button addActionListener(io.joshuasalcedo.webview4j.ui.event.ActionListener listener) { return addAction("onClick", listener); }
    public Button removeActionListener(io.joshuasalcedo.webview4j.ui.event.ActionListener listener) { return unlisten("onClick", listener); }
}
