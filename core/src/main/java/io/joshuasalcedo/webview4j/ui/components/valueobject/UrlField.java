package io.joshuasalcedo.webview4j.ui.components.valueobject;

import io.joshuasalcedo.webview4j.ui.Component;
import java.util.function.Consumer;

/**
 * A web address; one without "https://" gets it. The Java side of the kit's {@code UrlField}; its text is what a value object is made from, and the value
 * object's own check is given back as {@link #error}. {@link ValueField} does both for you.
 */
public final class UrlField extends Component<UrlField> {

    public UrlField(String label) {
        super("urlField");
        prop("label", label);
    }

    public UrlField value(String text) { return prop("value", text == null ? "" : text); }
    public UrlField hint(String hint) { return prop("hint", hint); }
    /** What is wrong with what was typed (a value object's message): shown instead of the hint. */
    public UrlField error(String error) { return prop("error", error); }
    public UrlField disabled(boolean disabled) { return prop("disabled", disabled); }
    /** The text as it changes. */
    public UrlField onChange(Consumer<String> action) { return on("onChange", v -> action.accept(v == null ? "" : String.valueOf(v))); }
}
