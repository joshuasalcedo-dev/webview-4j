package io.joshuasalcedo.webview4j.ui.components.valueobject;

import static io.joshuasalcedo.webview4j.ui.Ui.*;

import io.joshuasalcedo.valueobject.contact.Email;
import io.joshuasalcedo.valueobject.contact.Link;
import io.joshuasalcedo.valueobject.contact.PhoneNumber;
import io.joshuasalcedo.valueobject.exception.ValueObjectException;
import io.joshuasalcedo.valueobject.financial.FiatCurrency;
import io.joshuasalcedo.valueobject.financial.Money;
import io.joshuasalcedo.valueobject.financial.Percentage;
import io.joshuasalcedo.valueobject.geo.Country;
import io.joshuasalcedo.valueobject.temporal.CalendarDate;
import io.joshuasalcedo.webview4j.ui.Component;
import io.joshuasalcedo.webview4j.ui.Ui;
import io.joshuasalcedo.webview4j.ui.UiException;
import java.lang.reflect.Method;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

/**
 * Value objects shown the way each is read: money in its currency's way (₱499.00), an email to write to, a phone
 * number to call, a link to open, a country with its flag, a percentage, a date in the person's format; any other
 * value object as its text.
 * <pre>{@code
 * listItem(order.customer().displayName()).trailing(...)
 * row(text("Total"), ValueViews.show(order.total()))
 * }</pre>
 */
public final class ValueViews {

    static {
        // A value object's exception from a handler says what is wrong with what was typed: a warning, its own words.
        Ui.explain(ValueObjectException.class, e -> new UiException("Check what you typed",
                e.getMessage() == null ? "Something typed is not valid." : e.getMessage(), UiException.Level.WARNING, e));
    }

    private ValueViews() {}

    /** Turns on the handling of {@link ValueObjectException}s in screens (they show as a warning, in their words). */
    public static void install() {
        // the static block above
    }

    /** {@code value} as a component: the one that fits its type. */
    public static Component<?> show(Object value) {
        if (value == null) return text("—").tone(io.joshuasalcedo.webview4j.ui.components.Tone.TERTIARY);
        return switch (value) {
            case Money m -> money(m.amount().movePointRight(m.currency().decimalPlaces()).longValue(), m.currency().code());
            case Email e -> link(e.value(), "mailto:" + e.value());
            case PhoneNumber p -> link(p.formatted(), "tel:+" + p.toDigits().replaceFirst("^\\+", ""));
            case Link l -> link(l.value(), l.value());
            case Country c -> text(c.flagEmoji() + " " + c.displayName());
            case FiatCurrency c -> text(c.code() + " · " + c.displayName());
            case Percentage p -> text(p.toString());
            case CalendarDate d -> text(d.value().format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)));
            default -> text(name(value));
        };
    }

    /** What people call {@code value}: an enum's display name, otherwise its text. */
    public static String name(Object value) {
        if (value == null) return "";
        try {
            Method displayName = value.getClass().getMethod("displayName");
            if (displayName.getReturnType() == String.class) return (String) displayName.invoke(value);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // no display name: its text
        }
        return value.toString();
    }
}
