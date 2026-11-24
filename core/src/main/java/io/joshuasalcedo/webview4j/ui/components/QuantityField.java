package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** A quantity with its unit (2.5 kg), and a unit picker that converts (5.51 lb). */
public final class QuantityField extends Component<QuantityField> {

    public QuantityField(String label, Unit unit) {
        super("quantityField");
        prop("label", label);
        prop("unit", unit.key());
    }

    public QuantityField value(Double value) { return prop("value", value); }
    /** The units to choose from: of one kind (masses, lengths...). */
    public QuantityField units(java.util.List<Unit> units) { return prop("units", units.stream().map(Unit::key).toList()); }
    /** Changing the unit converts the number. */
    public QuantityField convert() { return prop("convert", true); }
    public QuantityField hint(String hint) { return prop("hint", hint); }
    public QuantityField error(String error) { return prop("error", error); }
    public QuantityField onChange(java.util.function.Consumer<Double> action) { return on("onChange", v -> action.accept(Keys.number(v))); }
    public QuantityField onUnitChange(java.util.function.Consumer<Unit> action) { return on("onUnitChange", v -> action.accept(Unit.of(Keys.text(v)))); }
}
