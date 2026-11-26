package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** A check box with its label. */
public final class Checkbox extends Component<Checkbox> {

    public Checkbox(String label) {
        super("checkbox");
        prop("label", label);
        keep("onChange", "checked", Keys::flag);
    }

    public Checkbox checked(boolean checked) { return prop("checked", checked); }
    public Checkbox disabled(boolean disabled) { return prop("disabled", disabled); }
    public Checkbox onChange(java.util.function.Consumer<Boolean> action) { return on("onChange", v -> action.accept(Keys.flag(v))); }

    // --- as Swing writes it ---

    /** Whether it is on now: as the user left it, or as set. */
    public boolean isSelected() { return Boolean.TRUE.equals(get("checked")); }
    public Checkbox setSelected(boolean selected) { return checked(selected); }
    /** Runs when the user turns it on or off; {@code e.getValue()} is whether it is on. */
    public Checkbox addChangeListener(io.joshuasalcedo.webview4j.ui.event.ChangeListener<Boolean> listener) { return addChange("onChange", listener, Keys::flag); }
    public Checkbox removeChangeListener(io.joshuasalcedo.webview4j.ui.event.ChangeListener<Boolean> listener) { return unlisten("onChange", listener); }
}
