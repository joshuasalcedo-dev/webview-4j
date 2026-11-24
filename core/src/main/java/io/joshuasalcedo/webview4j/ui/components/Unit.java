package io.joshuasalcedo.webview4j.ui.components;

import java.util.Locale;

/**
 * A unit {@link Quantity} and {@link QuantityField} know, written in the person's language ("2.5 kg", "37 °C").
 * Units of a kind convert to each other ({@link QuantityField#convert}).
 */
public enum Unit {
    MILLIMETER, CENTIMETER, METER, KILOMETER, INCH, FOOT, YARD, MILE,
    GRAM, KILOGRAM, OUNCE, POUND, STONE,
    MILLILITER, LITER, GALLON,
    MILLISECOND, SECOND, MINUTE, HOUR, DAY, WEEK,
    BYTE, KILOBYTE, MEGABYTE, GIGABYTE, TERABYTE,
    METER_PER_SECOND, KILOMETER_PER_HOUR, MILE_PER_HOUR,
    CELSIUS, FAHRENHEIT,
    PERCENT;

    /** As the page names it: {@code kilometer-per-hour}. */
    public String key() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    static Unit of(String key) {
        return valueOf(key.toUpperCase(Locale.ROOT).replace('-', '_'));
    }
}
