package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** A dropdown: the system's own list opens. */
public final class Select extends Component<Select> {

    public Select(String label, java.util.Map<String, String> options) {
        super("select");
        prop("label", label);
        prop("options", choices(options));
    }

    public Select value(String value) { return prop("value", value); }
    public Select disabled(boolean disabled) { return prop("disabled", disabled); }
    public Select onChange(java.util.function.Consumer<String> action) { return on("onChange", v -> action.accept(Keys.text(v))); }

    private static java.util.List<java.util.Map<String, String>> choices(java.util.Map<String, String> options) {
        java.util.List<java.util.Map<String, String>> list = new java.util.ArrayList<>();
        options.forEach((value, label) -> list.add(java.util.Map.of("value", value, "label", label)));
        return list;
    }
}
