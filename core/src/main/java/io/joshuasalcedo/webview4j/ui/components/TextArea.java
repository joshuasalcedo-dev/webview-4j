package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** A text box of several lines, that grows with what is typed. */
public final class TextArea extends Component<TextArea> {

    public TextArea(String label) {
        super("textArea");
        prop("label", label);
        keep("onChange", "value", Keys::text);
    }

    public TextArea value(String value) { return prop("value", value == null ? "" : value); }
    public TextArea placeholder(String placeholder) { return prop("placeholder", placeholder); }
    /** One line under it about what to type. */
    public TextArea hint(String hint) { return prop("hint", hint); }
    /** What is wrong with what was typed: shown instead of the hint, and the field is marked. */
    public TextArea error(String error) { return prop("error", error); }
    public TextArea disabled(boolean disabled) { return prop("disabled", disabled); }
    /** Each change as the user types: the whole text. */
    public TextArea onChange(java.util.function.Consumer<String> action) { return on("onChange", v -> action.accept(Keys.text(v))); }

    public TextArea rows(int rows) { return prop("rows", rows); }
    /** A limit, with a count under the box. */
    public TextArea maxLength(int length) { return prop("maxLength", length); }

    // --- as Swing writes it ---

    /** What is in it now: what the user typed, or what was set. */
    public String getText() { return get("value") instanceof String s ? s : ""; }
    /** Puts {@code text} in it; a window showing it draws again. */
    public TextArea setText(String text) { return value(text); }
    /** Runs on each change as the user types; {@code e.getValue()} is the whole text. */
    public TextArea addChangeListener(io.joshuasalcedo.webview4j.ui.event.ChangeListener<String> listener) { return addChange("onChange", listener, Keys::text); }
    public TextArea removeChangeListener(io.joshuasalcedo.webview4j.ui.event.ChangeListener<String> listener) { return unlisten("onChange", listener); }
}
