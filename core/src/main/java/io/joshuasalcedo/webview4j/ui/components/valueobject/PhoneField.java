package io.joshuasalcedo.webview4j.ui.components.valueobject;

import io.joshuasalcedo.webview4j.ui.Component;
import java.util.function.Consumer;

/**
 * A phone number with the country's code: the phone keyboard, 7 to 15 digits. The Java side of the kit's {@code PhoneField}; its text is what a value object is made from, and the value
 * object's own check is given back as {@link #error}. {@link ValueField} does both for you.
 */
public final class PhoneField extends Component<PhoneField> {

    public PhoneField(String label) {
        super("phoneField");
        prop("label", label);
    }

    public PhoneField value(String text) { return prop("value", text == null ? "" : text); }
    public PhoneField hint(String hint) { return prop("hint", hint); }
    /** What is wrong with what was typed (a value object's message): shown instead of the hint. */
    public PhoneField error(String error) { return prop("error", error); }
    public PhoneField disabled(boolean disabled) { return prop("disabled", disabled); }
    /** The text as it changes. */
    public PhoneField onChange(Consumer<String> action) { return on("onChange", v -> action.accept(v == null ? "" : String.valueOf(v))); }
}
