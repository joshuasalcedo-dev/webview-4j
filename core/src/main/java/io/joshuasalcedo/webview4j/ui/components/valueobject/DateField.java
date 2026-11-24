package io.joshuasalcedo.webview4j.ui.components.valueobject;

import io.joshuasalcedo.webview4j.ui.Component;
import java.util.function.Consumer;

/**
 * A date: the system's date picker. Its text is the ISO date (2026-11-25). The Java side of the kit's {@code DateField}; its text is what a value object is made from, and the value
 * object's own check is given back as {@link #error}. {@link ValueField} does both for you.
 */
public final class DateField extends Component<DateField> {

    public DateField(String label) {
        super("dateField");
        prop("label", label);
    }

    public DateField value(String text) { return prop("value", text == null ? "" : text); }
    public DateField hint(String hint) { return prop("hint", hint); }
    /** What is wrong with what was typed (a value object's message): shown instead of the hint. */
    public DateField error(String error) { return prop("error", error); }
    public DateField disabled(boolean disabled) { return prop("disabled", disabled); }
    /** The text as it changes. */
    public DateField onChange(Consumer<String> action) { return on("onChange", v -> action.accept(v == null ? "" : String.valueOf(v))); }
}
