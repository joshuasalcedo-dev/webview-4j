package io.joshuasalcedo.webview4j.ui.components.valueobject;

import io.joshuasalcedo.webview4j.ui.Component;
import java.util.function.Consumer;

/**
 * A card number in groups of 4, its brand shown, checked with its check digit. The Java side of the kit's {@code CardNumberField}; its text is what a value object is made from, and the value
 * object's own check is given back as {@link #error}. {@link ValueField} does both for you.
 */
public final class CardNumberField extends Component<CardNumberField> {

    public CardNumberField(String label) {
        super("cardNumberField");
        prop("label", label);
    }

    public CardNumberField value(String text) { return prop("value", text == null ? "" : text); }
    public CardNumberField hint(String hint) { return prop("hint", hint); }
    /** What is wrong with what was typed (a value object's message): shown instead of the hint. */
    public CardNumberField error(String error) { return prop("error", error); }
    public CardNumberField disabled(boolean disabled) { return prop("disabled", disabled); }
    /** The text as it changes. */
    public CardNumberField onChange(Consumer<String> action) { return on("onChange", v -> action.accept(v == null ? "" : String.valueOf(v))); }
}
