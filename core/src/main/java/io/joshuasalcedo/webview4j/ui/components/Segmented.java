package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** One of a few choices, side by side: macOS' segmented control, Windows' SelectorBar. */
public final class Segmented extends Component<Segmented> {

    public Segmented(String label, java.util.Map<String, String> options) {
        super("segmented");
        prop("label", label);
        prop("options", choices(options));
    }

    public Segmented value(String value) { return prop("value", value); }
    public Segmented onChange(java.util.function.Consumer<String> action) { return on("onChange", v -> action.accept(Keys.text(v))); }

    private static java.util.List<java.util.Map<String, String>> choices(java.util.Map<String, String> options) {
        java.util.List<java.util.Map<String, String>> list = new java.util.ArrayList<>();
        options.forEach((value, label) -> list.add(java.util.Map.of("value", value, "label", label)));
        return list;
    }
}
