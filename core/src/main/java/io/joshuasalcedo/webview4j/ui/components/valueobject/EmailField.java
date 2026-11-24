package io.joshuasalcedo.webview4j.ui.components.valueobject;

import io.joshuasalcedo.webview4j.ui.Component;
import java.util.function.Consumer;

/**
 * An email address: the email keyboard, autofill, and a quick check of its form as the person leaves the field. The Java side of the kit's {@code EmailField}; its text is what a value object is made from, and the value
 * object's own check is given back as {@link #error}. {@link ValueField} does both for you.
 */
public final class EmailField extends Component<EmailField> {

    public EmailField(String label) {
        super("emailField");
        prop("label", label);
    }

    public EmailField value(String text) { return prop("value", text == null ? "" : text); }
    public EmailField hint(String hint) { return prop("hint", hint); }
    /** What is wrong with what was typed (a value object's message): shown instead of the hint. */
    public EmailField error(String error) { return prop("error", error); }
    public EmailField disabled(boolean disabled) { return prop("disabled", disabled); }
    /** The text as it changes. */
    public EmailField onChange(Consumer<String> action) { return on("onChange", v -> action.accept(v == null ? "" : String.valueOf(v))); }
}
