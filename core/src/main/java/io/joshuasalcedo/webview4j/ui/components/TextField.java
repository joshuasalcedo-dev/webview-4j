package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** A one-line text box, with its label and a hint or an error. */
public final class TextField extends Component<TextField> {

    public TextField(String label) {
        super("textField");
        prop("label", label);
    }

    public TextField value(String value) { return prop("value", value == null ? "" : value); }
    public TextField placeholder(String placeholder) { return prop("placeholder", placeholder); }
    /** One line under it about what to type. */
    public TextField hint(String hint) { return prop("hint", hint); }
    /** What is wrong with what was typed: shown instead of the hint, and the field is marked. */
    public TextField error(String error) { return prop("error", error); }
    public TextField disabled(boolean disabled) { return prop("disabled", disabled); }
    /** Each change as the user types: the whole text. */
    public TextField onChange(java.util.function.Consumer<String> action) { return on("onChange", v -> action.accept(Keys.text(v))); }

    public TextField password() { return prop("password", true); }
    /** Enter pressed in the field. */
    public TextField onSubmit(java.util.function.Consumer<String> action) { return on("onSubmit", v -> action.accept(Keys.text(v))); }
}
