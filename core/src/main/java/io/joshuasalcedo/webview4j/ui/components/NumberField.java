package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** A number box: Windows' arrows, a Mac's stepper; ↑ ↓ and Page Up/Down; written in the person's number format. */
public final class NumberField extends Component<NumberField> {

    public NumberField(String label) {
        super("numberField");
        prop("label", label);
        keep("onChange", "value", Keys::number);
    }

    public NumberField value(Double value) { return prop("value", value); }
    public NumberField min(double min) { return prop("min", min); }
    public NumberField max(double max) { return prop("max", max); }
    public NumberField step(double step) { return prop("step", step); }
    public NumberField fractionDigits(int digits) { return prop("fractionDigits", digits); }
    /** A unit or symbol written after the number ("kg", "%"). */
    public NumberField after(String text) { return prop("after", text); }
    public NumberField before(String text) { return prop("before", text); }
    public NumberField hint(String hint) { return prop("hint", hint); }
    public NumberField error(String error) { return prop("error", error); }
    /** The number as it changes; null when the box is empty. */
    public NumberField onChange(java.util.function.Consumer<Double> action) { return on("onChange", v -> action.accept(Keys.number(v))); }

    // --- as Swing writes it ---

    /** The number in it now, or null when it is empty. */
    public Double getValue() { return get("value") instanceof Number n ? n.doubleValue() : null; }
    public NumberField setValue(Double value) { return value(value); }
    /** Runs when the number changes; {@code e.getValue()} is null when the box is empty. */
    public NumberField addChangeListener(io.joshuasalcedo.webview4j.ui.event.ChangeListener<Double> listener) { return addChange("onChange", listener, Keys::number); }
    public NumberField removeChangeListener(io.joshuasalcedo.webview4j.ui.event.ChangeListener<Double> listener) { return unlisten("onChange", listener); }
}
