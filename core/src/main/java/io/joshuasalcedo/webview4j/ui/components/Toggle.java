package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** An on/off switch (Windows' ToggleSwitch, macOS' switch). */
public final class Toggle extends Component<Toggle> {

    public Toggle(String label) {
        super("switch");
        prop("label", label);
        keep("onChange", "checked", Keys::flag);
    }

    public Toggle checked(boolean checked) { return prop("checked", checked); }
    public Toggle disabled(boolean disabled) { return prop("disabled", disabled); }
    public Toggle onChange(java.util.function.Consumer<Boolean> action) { return on("onChange", v -> action.accept(Keys.flag(v))); }

    // --- as Swing writes it ---

    /** Whether it is on now: as the user left it, or as set. */
    public boolean isSelected() { return Boolean.TRUE.equals(get("checked")); }
    public Toggle setSelected(boolean selected) { return checked(selected); }
    /** Runs when the user turns it on or off; {@code e.getValue()} is whether it is on. */
    public Toggle addChangeListener(io.joshuasalcedo.webview4j.ui.event.ChangeListener<Boolean> listener) { return addChange("onChange", listener, Keys::flag); }
    public Toggle removeChangeListener(io.joshuasalcedo.webview4j.ui.event.ChangeListener<Boolean> listener) { return unlisten("onChange", listener); }
}
