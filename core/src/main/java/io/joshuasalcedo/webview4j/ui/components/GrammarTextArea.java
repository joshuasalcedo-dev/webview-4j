package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;
import java.util.function.Consumer;

/**
 * A text area that checks spelling and grammar as it is typed: Harper, in the page as WebAssembly (no round trip
 * to Java). Mistakes are underlined; the one at the caret shows what it is and its fixes. To check text in Java,
 * use {@link io.joshuasalcedo.webview4j.Grammar}.
 * <pre>{@code
 * grammarTextArea("Message").value(message).onChange(v -> message = v).dialect("en-GB")
 * }</pre>
 */
public final class GrammarTextArea extends Component<GrammarTextArea> {

    public GrammarTextArea(String label) {
        super("grammarTextArea");
        prop("label", label);
    }

    public GrammarTextArea value(String value) { return prop("value", value); }

    public GrammarTextArea onChange(Consumer<String> action) { return on("onChange", v -> action.accept(Keys.text(v))); }

    /** Which English: en-US (the default), en-GB, en-AU, en-CA, en-IN. */
    public GrammarTextArea dialect(String dialect) { return prop("dialect", dialect); }

    /** How many lines tall (4 unless set). */
    public GrammarTextArea rows(int rows) { return prop("rows", rows); }

    public GrammarTextArea placeholder(String placeholder) { return prop("placeholder", placeholder); }

    /** Checks as it is typed (on unless false). */
    public GrammarTextArea check(boolean check) { return prop("check", check); }

    /** How long typing must pause before the text is checked (500 ms unless set): no check on every key. */
    public GrammarTextArea checkDelay(java.time.Duration pause) {
        if (pause.isNegative()) throw new IllegalArgumentException("checkDelay cannot be negative: " + pause);
        return prop("checkDelay", pause.toMillis());
    }

    /** How many mistakes there are, each time the text is checked (e.g. to warn before it is sent). */
    public GrammarTextArea onMistakes(Consumer<Integer> action) {
        return on("onMistakes", v -> action.accept(v instanceof Number n ? n.intValue() : 0));
    }
}
