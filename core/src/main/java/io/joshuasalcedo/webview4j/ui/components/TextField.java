package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** A one-line text box, with its label and a hint or an error. */
public final class TextField extends Component<TextField> {

    public TextField(String label) {
        super("textField");
        prop("label", label);
        keep("onChange", "value", Keys::text);
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

    /**
     * Which characters can be typed, as one regular-expression character class: {@code "[0-9]"} for digits only,
     * {@code "[A-Za-z0-9_-]"} for a user name. Anything else does not appear as it is typed, and is left out of
     * what is pasted.
     *
     * @throws IllegalArgumentException if it is not one character class
     */
    public TextField allow(String characterClass) {
        if (characterClass == null || !characterClass.matches("\\[\\^?(?:[^\\]\\\\]|\\\\.)+\\]")) {
            throw new IllegalArgumentException("allow takes one character class, e.g. [0-9] or [A-Za-z_-]: " + characterClass);
        }
        java.util.regex.Pattern.compile(characterClass);   // a class Java cannot read, the page cannot either
        return prop("allow", characterClass);
    }

    /** At most this many characters: the next one typed does not appear. */
    public TextField maxLength(int characters) {
        if (characters < 0) throw new IllegalArgumentException("maxLength cannot be negative: " + characters);
        return prop("maxLength", characters);
    }
    /** Enter pressed in the field. */
    public TextField onSubmit(java.util.function.Consumer<String> action) { return on("onSubmit", v -> action.accept(Keys.text(v))); }

    // --- as Swing writes it ---

    /** What is in it now: what the user typed, or what was set. */
    public String getText() { return get("value") instanceof String s ? s : ""; }
    /** Puts {@code text} in it; a window showing it draws again. */
    public TextField setText(String text) { return value(text); }
    /** Runs on each change as the user types; {@code e.getValue()} is the whole text. */
    public TextField addChangeListener(io.joshuasalcedo.webview4j.ui.event.ChangeListener<String> listener) { return addChange("onChange", listener, Keys::text); }
    public TextField removeChangeListener(io.joshuasalcedo.webview4j.ui.event.ChangeListener<String> listener) { return unlisten("onChange", listener); }
    /** Runs when Enter is pressed in it (Swing's JTextField action). */
    public TextField addActionListener(io.joshuasalcedo.webview4j.ui.event.ActionListener listener) { return addAction("onSubmit", listener); }
    public TextField removeActionListener(io.joshuasalcedo.webview4j.ui.event.ActionListener listener) { return unlisten("onSubmit", listener); }
}
