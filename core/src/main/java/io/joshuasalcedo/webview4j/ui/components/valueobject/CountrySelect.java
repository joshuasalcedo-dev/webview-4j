package io.joshuasalcedo.webview4j.ui.components.valueobject;

import io.joshuasalcedo.webview4j.ui.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A country: its flag and name. Its value is the two-letter code (PH). The Java side of the kit's {@code CountrySelect}: its own list unless {@link #options} gives one.
 */
public final class CountrySelect extends Component<CountrySelect> {

    public CountrySelect(String label) {
        super("countrySelect");
        prop("label", label);
    }

    public CountrySelect value(String code) { return prop("value", code == null ? "" : code); }
    /** A "none" choice at the top: for a field that may stay empty. */
    public CountrySelect optional() { return prop("optional", true); }
    public CountrySelect disabled(boolean disabled) { return prop("disabled", disabled); }
    /** The choices, by code, with what is shown for each (in their order). */
    public CountrySelect options(Map<String, String> options) {
        List<Map<String, String>> list = new ArrayList<>();
        options.forEach((value, label) -> list.add(Map.of("value", value, "label", label)));
        return prop("options", list);
    }
    /** The code chosen. */
    public CountrySelect onChange(Consumer<String> action) { return on("onChange", v -> action.accept(v == null ? "" : String.valueOf(v))); }
}
