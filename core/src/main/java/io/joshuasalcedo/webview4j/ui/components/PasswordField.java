package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** A password box: shown while held on Windows, a toggle on a Mac, and a Caps Lock warning. */
public final class PasswordField extends Component<PasswordField> {

    public PasswordField(String label) {
        super("passwordField");
        prop("label", label);
        keep("onChange", "value", Keys::text);
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

    // --- as Swing writes it ---

    /** What is in it now: what the user typed, or what was set. */
    public String getText() { return get("value") instanceof String s ? s : ""; }
    /** Puts {@code text} in it; a window showing it draws again. */
    public PasswordField setText(String text) { return value(text); }
    /** Runs on each change as the user types; {@code e.getValue()} is the whole text. */
    public PasswordField addChangeListener(io.joshuasalcedo.webview4j.ui.event.ChangeListener<String> listener) { return addChange("onChange", listener, Keys::text); }
    public PasswordField removeChangeListener(io.joshuasalcedo.webview4j.ui.event.ChangeListener<String> listener) { return unlisten("onChange", listener); }
}
