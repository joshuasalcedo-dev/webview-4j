package io.joshuasalcedo.webview4j.ui.components.valueobject;

import io.joshuasalcedo.webview4j.ui.Component;
import java.util.function.Consumer;

/**
 * An IBAN, in capitals and groups of 4 as it is typed, checked with its check digits. The Java side of the kit's {@code IbanField}; its text is what a value object is made from, and the value
 * object's own check is given back as {@link #error}. {@link ValueField} does both for you.
 */
public final class IbanField extends Component<IbanField> {

    public IbanField(String label) {
        super("ibanField");
        prop("label", label);
    }

    public IbanField value(String text) { return prop("value", text == null ? "" : text); }
    public IbanField hint(String hint) { return prop("hint", hint); }
    /** What is wrong with what was typed (a value object's message): shown instead of the hint. */
    public IbanField error(String error) { return prop("error", error); }
    public IbanField disabled(boolean disabled) { return prop("disabled", disabled); }
    /** The text as it changes. */
    public IbanField onChange(Consumer<String> action) { return on("onChange", v -> action.accept(v == null ? "" : String.valueOf(v))); }
}
