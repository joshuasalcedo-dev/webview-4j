package io.joshuasalcedo.webview4j.ui.components.valueobject;

import io.joshuasalcedo.webview4j.ui.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A language: its name in the person's language. Its value is the ISO 639 code (en). The Java side of the kit's {@code LanguageSelect}: its own list unless {@link #options} gives one.
 */
public final class LanguageSelect extends Component<LanguageSelect> {

    public LanguageSelect(String label) {
        super("languageSelect");
        prop("label", label);
    }

    public LanguageSelect value(String code) { return prop("value", code == null ? "" : code); }
    /** A "none" choice at the top: for a field that may stay empty. */
    public LanguageSelect optional() { return prop("optional", true); }
    public LanguageSelect disabled(boolean disabled) { return prop("disabled", disabled); }
    /** The choices, by code, with what is shown for each (in their order). */
    public LanguageSelect options(Map<String, String> options) {
        List<Map<String, String>> list = new ArrayList<>();
        options.forEach((value, label) -> list.add(Map.of("value", value, "label", label)));
        return prop("options", list);
    }
    /** The code chosen. */
    public LanguageSelect onChange(Consumer<String> action) { return on("onChange", v -> action.accept(v == null ? "" : String.valueOf(v))); }
}
