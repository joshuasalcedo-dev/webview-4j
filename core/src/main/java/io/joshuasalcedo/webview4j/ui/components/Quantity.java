package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** A quantity as text in the person's language (2.5 kg, 37 °C, 1.2 GB). */
public final class Quantity extends Component<Quantity> {

    public Quantity(double value, Unit unit) {
        super("quantity");
        prop("value", value);
        prop("unit", unit.key());
    }

    /** Converted to another unit of the same kind. */
    public Quantity as(Unit unit) { return prop("as", unit.key()); }
    public Quantity digits(int digits) { return prop("digits", digits); }
    /** "short" (the default), "long" or "narrow". */
    public Quantity display(String display) { return prop("display", display); }
}
