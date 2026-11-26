package io.joshuasalcedo.webview4j.plugin.authentication;

import io.joshuasalcedo.webview4j.ui.Component;
import java.util.function.Consumer;

/**
 * A one-time code (two-step sign-in, an email's code): a box per digit, pasted at once or typed. The Java side of the
 * kit's {@code CodeField}.
 */
public final class CodeField extends Component<CodeField> {

    public CodeField(String label) {
        super("codeField");
        prop("label", label);
    }

    /** How many digits (6 unless set). */
    public CodeField length(int digits) { return prop("length", digits); }
    public CodeField value(String code) { return prop("value", code); }
    public CodeField disabled(boolean disabled) { return prop("disabled", disabled); }
    public CodeField error(String message) { return prop("error", message); }
    public CodeField onChange(Consumer<String> action) { return on("onChange", v -> action.accept(String.valueOf(v))); }
    /** Every box has a digit: the code. */
    public CodeField onComplete(Consumer<String> action) { return on("onComplete", v -> action.accept(String.valueOf(v))); }
}
