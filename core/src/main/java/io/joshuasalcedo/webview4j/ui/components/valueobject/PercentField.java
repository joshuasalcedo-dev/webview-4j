package io.joshuasalcedo.webview4j.ui.components.valueobject;

import io.joshuasalcedo.webview4j.ui.Component;
import java.util.function.Consumer;

/**
 * A percentage: a number with "%" after it. Its text is "12.5%". The Java side of the kit's {@code PercentField}; its text is what a value object is made from, and the value
 * object's own check is given back as {@link #error}. {@link ValueField} does both for you.
 */
public final class PercentField extends Component<PercentField> {

    public PercentField(String label) {
        super("percentField");
        prop("label", label);
    }

    public PercentField value(String text) { return prop("value", text == null ? "" : text); }
    public PercentField hint(String hint) { return prop("hint", hint); }
    /** What is wrong with what was typed (a value object's message): shown instead of the hint. */
    public PercentField error(String error) { return prop("error", error); }
    public PercentField disabled(boolean disabled) { return prop("disabled", disabled); }
    /** The text as it changes. */
    public PercentField onChange(Consumer<String> action) { return on("onChange", v -> action.accept(v == null ? "" : String.valueOf(v))); }
}
