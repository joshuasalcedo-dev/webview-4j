package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** A password box: shown while held on Windows, a toggle on a Mac, and a Caps Lock warning. */
public final class PasswordField extends Component<PasswordField> {

    public PasswordField(String label) {
        super("passwordField");
        prop("label", label);
    }

    public PasswordField value(String value) { return prop("value", value == null ? "" : value); }
    public PasswordField placeholder(String placeholder) { return prop("placeholder", placeholder); }
    /** One line under it about what to type. */
    public PasswordField hint(String hint) { return prop("hint", hint); }
    /** What is wrong with what was typed: shown instead of the hint, and the field is marked. */
    public PasswordField error(String error) { return prop("error", error); }
    public PasswordField disabled(boolean disabled) { return prop("disabled", disabled); }
    /** Each change as the user types: the whole text. */
    public PasswordField onChange(java.util.function.Consumer<String> action) { return on("onChange", v -> action.accept(Keys.text(v))); }
}
