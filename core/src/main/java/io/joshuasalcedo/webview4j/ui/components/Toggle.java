package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** An on/off switch (Windows' ToggleSwitch, macOS' switch). */
public final class Toggle extends Component<Toggle> {

    public Toggle(String label) {
        super("switch");
        prop("label", label);
    }

    public Toggle checked(boolean checked) { return prop("checked", checked); }
    public Toggle disabled(boolean disabled) { return prop("disabled", disabled); }
    public Toggle onChange(java.util.function.Consumer<Boolean> action) { return on("onChange", v -> action.accept(Keys.flag(v))); }
}
