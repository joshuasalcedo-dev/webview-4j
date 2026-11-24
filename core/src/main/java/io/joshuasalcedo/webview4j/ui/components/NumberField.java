package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** A number box: Windows' arrows, a Mac's stepper; ↑ ↓ and Page Up/Down; written in the person's number format. */
public final class NumberField extends Component<NumberField> {

    public NumberField(String label) {
        super("numberField");
        prop("label", label);
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
}
