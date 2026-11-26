package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** A dropdown: the system's own list opens. */
public final class Select extends Component<Select> {

    public Select(String label, java.util.Map<String, String> options) {
        super("select");
        prop("label", label);
        prop("options", choices(options));
        keep("onChange", "value", Keys::text);
    }

    public Select value(String value) { return prop("value", value); }
    /** Offers {@code options} now (a value and what is shown for it, in their order); a window showing it draws again. */
    public Select setOptions(java.util.Map<String, String> options) { return prop("options", choices(options)); }
    public Select disabled(boolean disabled) { return prop("disabled", disabled); }
    public Select onChange(java.util.function.Consumer<String> action) { return on("onChange", v -> action.accept(Keys.text(v))); }

    private static java.util.List<java.util.Map<String, String>> choices(java.util.Map<String, String> options) {
        java.util.List<java.util.Map<String, String>> list = new java.util.ArrayList<>();
        options.forEach((value, label) -> list.add(java.util.Map.of("value", value, "label", label)));
        return list;
    }

    // --- as Swing writes it ---

    /** The value of the choice picked now (a key of the options), or null. */
    public String getSelectedValue() { return get("value") instanceof String s ? s : null; }
    /** Picks the choice whose value is {@code value}. */
    public Select setSelectedValue(String value) { return value(value); }
    /** Runs when the user picks another choice; {@code e.getValue()} is its value. */
    public Select addChangeListener(io.joshuasalcedo.webview4j.ui.event.ChangeListener<String> listener) { return addChange("onChange", listener, Keys::text); }
    public Select removeChangeListener(io.joshuasalcedo.webview4j.ui.event.ChangeListener<String> listener) { return unlisten("onChange", listener); }
}
