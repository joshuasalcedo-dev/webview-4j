package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** A check box with its label. */
public final class Checkbox extends Component<Checkbox> {

    public Checkbox(String label) {
        super("checkbox");
        prop("label", label);
    }

    public Checkbox checked(boolean checked) { return prop("checked", checked); }
    public Checkbox disabled(boolean disabled) { return prop("disabled", disabled); }
    public Checkbox onChange(java.util.function.Consumer<Boolean> action) { return on("onChange", v -> action.accept(Keys.flag(v))); }
}
