package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** One of a few choices, side by side: macOS' segmented control, Windows' SelectorBar. */
public final class Segmented extends Component<Segmented> {

    public Segmented(String label, java.util.Map<String, String> options) {
        super("segmented");
        prop("label", label);
        prop("options", choices(options));
        keep("onChange", "value", Keys::text);
    }

    public Segmented value(String value) { return prop("value", value); }
    public Segmented onChange(java.util.function.Consumer<String> action) { return on("onChange", v -> action.accept(Keys.text(v))); }

    private static java.util.List<java.util.Map<String, String>> choices(java.util.Map<String, String> options) {
        java.util.List<java.util.Map<String, String>> list = new java.util.ArrayList<>();
        options.forEach((value, label) -> list.add(java.util.Map.of("value", value, "label", label)));
        return list;
    }

    // --- as Swing writes it ---

    /** The value of the choice picked now (a key of the options), or null. */
    public String getSelectedValue() { return get("value") instanceof String s ? s : null; }
    /** Picks the choice whose value is {@code value}. */
    public Segmented setSelectedValue(String value) { return value(value); }
    /** Runs when the user picks another choice; {@code e.getValue()} is its value. */
    public Segmented addChangeListener(io.joshuasalcedo.webview4j.ui.event.ChangeListener<String> listener) { return addChange("onChange", listener, Keys::text); }
    public Segmented removeChangeListener(io.joshuasalcedo.webview4j.ui.event.ChangeListener<String> listener) { return unlisten("onChange", listener); }
}
