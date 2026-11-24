package io.joshuasalcedo.webview4j.ui.components.valueobject;

import static io.joshuasalcedo.webview4j.ui.Ui.*;

import io.joshuasalcedo.valueobject.contact.Email;
import io.joshuasalcedo.valueobject.contact.Link;
import io.joshuasalcedo.valueobject.contact.PhoneNumber;
import io.joshuasalcedo.valueobject.financial.FiatCurrency;
import io.joshuasalcedo.valueobject.financial.Money;
import io.joshuasalcedo.valueobject.financial.Percentage;
import io.joshuasalcedo.valueobject.geo.Country;
import io.joshuasalcedo.valueobject.temporal.CalendarDate;
import io.joshuasalcedo.webview4j.ui.Component;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * A field that is a value object: it keeps what the person typed, reads it with the type's own {@code parse} as they
 * type, and shows the type's own message (written for people) under the field when it does not fit. Keep it in a
 * screen's field; {@link #value()} is the value object once it is one.
 * <pre>{@code
 * final ValueField<Email> email = ValueField.email();
 * final ValueField<Money> price = ValueField.money(FiatCurrency.PHP);
 * final ValueField<Country> country = ValueField.of(Country.class);
 *
 * public Component<?> render() {
 *     return column(
 *             email.field("Email"),
 *             price.field("Price"),
 *             country.field("Country"),
 *             button("Save").accent().disabled(!email.isValid() || !price.isValid())
 *                     .onClick(() -> save(email.value().orElseThrow(), price.value().orElseThrow())));
 * }
 * }</pre>
 *
 * <p>Any type with a static {@code parse(String)} works ({@link #of}): an enum ({@code Country}, {@code FiatCurrency},
 * {@code Language}...) is a dropdown of its constants; {@link Money} is a money field with a currency picker;
 * {@link Percentage} a number with "%"; anything else a text field.
 *
 * @param <T> the value object's type
 */
public final class ValueField<T> {

    private final Class<T> type;
    private final Function<String, T> parser;
    private String text = "";
    private T value;
    private String error;
    private boolean required;
    private boolean touched;
    private String hint;
    private FiatCurrency currency = FiatCurrency.USD;

    private ValueField(Class<T> type, Function<String, T> parser) {
        this.type = Objects.requireNonNull(type, "type");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.hint = defaultHint(type);
    }

    /**
     * A field of any value object with a static {@code parse(String)} (it throws with a message for people when the
     * text is not one).
     *
     * @throws IllegalArgumentException if {@code type} has no such method
     */
    public static <T> ValueField<T> of(Class<T> type) {
        Method parse;
        try {
            parse = type.getMethod("parse", String.class);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(type.getName() + " has no static parse(String): give ValueField.of a parser", e);
        }
        if (!Modifier.isStatic(parse.getModifiers())) throw new IllegalArgumentException(type.getName() + ".parse is not static");
        return new ValueField<>(type, text -> {
            try {
                return type.cast(parse.invoke(null, text));
            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof RuntimeException r) throw r;
                throw new IllegalArgumentException(e.getCause());
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    /** A field read with {@code parser} (it throws, with a message for people, when the text does not fit). */
    public static <T> ValueField<T> of(Class<T> type, Function<String, T> parser) {
        return new ValueField<>(type, parser);
    }

    public static ValueField<Email> email() { return of(Email.class); }

    public static ValueField<PhoneNumber> phone() { return of(PhoneNumber.class); }

    public static ValueField<Link> link() { return of(Link.class); }

    /** A date, typed in any printed form ("2026-11-25", "25 Nov 2026", "11/25/2026"). */
    public static ValueField<CalendarDate> date() { return of(CalendarDate.class); }

    public static ValueField<Percentage> percentage() { return of(Percentage.class); }

    public static ValueField<Country> country() { return of(Country.class); }

    public static ValueField<FiatCurrency> currency() { return of(FiatCurrency.class); }

    /** An amount of money, in {@code currency} unless the person picks another. */
    public static ValueField<Money> money(FiatCurrency currency) {
        ValueField<Money> field = of(Money.class);
        field.currency = Objects.requireNonNull(currency, "currency");
        return field;
    }

    // ---- its value ----

    /** The value object, when what was typed is one. */
    public Optional<T> value() {
        return Optional.ofNullable(value);
    }

    /** Whether it holds a value object (or is empty and not {@link #required}). */
    public boolean isValid() {
        return error == null && (value != null || (!required && text.isBlank()));
    }

    public boolean isEmpty() {
        return text.isBlank();
    }

    /** What was typed, as typed. */
    public String text() {
        return text;
    }

    /** Why what was typed is not a value object; empty when it is (or nothing was typed yet). */
    public Optional<String> error() {
        return Optional.ofNullable(touched ? error : null);
    }

    /** Fills it in with {@code value} (null empties it). */
    public ValueField<T> set(T value) {
        this.value = value;
        this.text = value == null ? "" : print(value);
        this.error = null;
        return this;
    }

    /** Types {@code text} in, as the person would: read, and checked. */
    public ValueField<T> type(String text) {
        this.text = text == null ? "" : text;
        this.touched = true;
        read();
        return this;
    }

    /** Empty is a problem: "Required". */
    public ValueField<T> required() {
        this.required = true;
        return this;
    }

    /** The line under the field about what to type, instead of the type's own. */
    public ValueField<T> hint(String hint) {
        this.hint = hint;
        return this;
    }

    /** Marks it, as a form does on Save: an empty required field then says so. */
    public boolean check() {
        touched = true;
        read();
        return isValid();
    }

    private void read() {
        if (text.isBlank()) {
            value = null;
            error = required ? "Required" : null;
            return;
        }
        try {
            value = parser.apply(text.strip());
            error = value == null ? "Not a valid " + words(type.getSimpleName()) : null;
        } catch (RuntimeException e) {
            value = null;
            error = e.getMessage() != null && !e.getMessage().isBlank() ? e.getMessage() : "Not a valid " + words(type.getSimpleName());
        }
    }

    // ---- the field ----

    /** The field, labelled {@code label}: the component that fits the type. */
    public Component<?> field(String label) {
        String shown = label + (required ? "" : " (optional)");
        String problem = error().orElse(null);
        if (type.isEnum()) return choice(shown);
        if (type == Money.class) return money(shown, problem);
        if (type == Percentage.class) {
            Double n = value instanceof Percentage p ? p.value().doubleValue() : null;
            var f = numberField(shown).value(n).after("%").onChange(v -> type(v == null ? "" : BigDecimal.valueOf(v).toPlainString() + "%"));
            return problem != null ? f.error(problem) : hint != null ? f.hint(hint) : f;
        }
        var f = textField(shown).value(text).onChange(this::type);
        return problem != null ? f.error(problem) : hint != null ? f.hint(hint) : f;
    }

    private Component<?> choice(String label) {
        Map<String, String> options = new LinkedHashMap<>();
        if (!required) options.put("", "—");
        for (Object constant : type.getEnumConstants()) {
            if (constant instanceof io.joshuasalcedo.valueobject.geo.Country c && c.isUnknown()) continue;
            if (constant instanceof FiatCurrency c && c.isUnknown()) continue;
            options.put(((Enum<?>) constant).name(), ValueViews.name(constant));
        }
        String current = value instanceof Enum<?> e ? e.name() : "";
        return select(label, options).value(current).onChange(v -> type(v)).width(320);
    }

    private Component<?> money(String label, String problem) {
        FiatCurrency shownIn = value instanceof Money m && m.currency() instanceof FiatCurrency c ? c : currency;
        Long minor = value instanceof Money m ? m.amount().movePointRight(shownIn.decimalPlaces()).longValue() : null;
        List<String> currencies = List.of("PHP", "USD", "EUR", "JPY", "GBP", "SGD", "AUD", "CAD");
        var f = moneyField(label, shownIn.code()).value(minor).currencies(currencies)
                .onChange(v -> {
                    if (v == null) type("");
                    else set(fit(Money.of(BigDecimal.valueOf(v).movePointLeft(currencyOf().decimalPlaces()), currencyOf())));
                })
                .onCurrencyChange(code -> {
                    currency = FiatCurrency.parse(code);
                    if (value instanceof Money m) set(fit(Money.of(m.amount(), currency)));
                });
        return problem != null ? f.error(problem) : f;
    }

    private FiatCurrency currencyOf() {
        return value instanceof Money m && m.currency() instanceof FiatCurrency c ? c : currency;
    }

    @SuppressWarnings("unchecked")
    private T fit(Object o) {
        touched = true;
        return (T) o;
    }

    private String print(T value) {
        if (value instanceof Enum<?> e) return e.name();
        if (value instanceof Money m) return m.amount().toPlainString() + " " + m.currency().code();
        if (value instanceof PhoneNumber p) return p.formatted();
        return value.toString();
    }

    private static String defaultHint(Class<?> type) {
        if (type == Email.class) return "An email address, as ada@example.com";
        if (type == PhoneNumber.class) return "With the country's code, as +63 917 555 0142";
        if (type == Link.class) return "An address, as https://example.com";
        if (type == CalendarDate.class) return "A date, in any form: 2026-11-25, 25 Nov 2026";
        return null;
    }

    static String words(String name) {
        String spaced = name.replaceAll("([a-z0-9])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT);
        return spaced;
    }
}
