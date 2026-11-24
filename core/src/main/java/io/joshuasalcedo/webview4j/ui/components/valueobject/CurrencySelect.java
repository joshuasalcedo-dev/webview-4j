package io.joshuasalcedo.webview4j.ui.components.valueobject;

import io.joshuasalcedo.webview4j.ui.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A currency: its code and name. Its value is the ISO code (PHP). The Java side of the kit's {@code CurrencySelect}: its own list unless {@link #options} gives one.
 */
public final class CurrencySelect extends Component<CurrencySelect> {

    public CurrencySelect(String label) {
        super("currencySelect");
        prop("label", label);
    }

    public CurrencySelect value(String code) { return prop("value", code == null ? "" : code); }
    /** A "none" choice at the top: for a field that may stay empty. */
    public CurrencySelect optional() { return prop("optional", true); }
    public CurrencySelect disabled(boolean disabled) { return prop("disabled", disabled); }
    /** The choices, by code, with what is shown for each (in their order). */
    public CurrencySelect options(Map<String, String> options) {
        List<Map<String, String>> list = new ArrayList<>();
        options.forEach((value, label) -> list.add(Map.of("value", value, "label", label)));
        return prop("options", list);
    }
    /** The code chosen. */
    public CurrencySelect onChange(Consumer<String> action) { return on("onChange", v -> action.accept(v == null ? "" : String.valueOf(v))); }
}
