package io.joshuasalcedo.swaswa.css;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

/**
 * CSS declarations, in order: an element's own style ({@code Component.getStyle()}) or a rule's
 * ({@link StyleSheet#rule}). Properties typed ({@link CssProperty}) or by name, custom ones ({@code --gap}) too; values
 * as CSS writes them. Light and dark: {@link #lightDark(String, String)} is a colour for each.
 * <pre>{@code
 * Style.of().set(CssProperty.DISPLAY, "grid").set(CssProperty.GAP, "8px")
 *         .set(CssProperty.COLOR, Style.lightDark("#1b1d22", "#e4e8ef"))
 * }</pre>
 */
public final class Style {

    private static final Pattern PROPERTY = Pattern.compile("--[A-Za-z0-9_-]+|-?[a-z][a-z0-9-]*");

    private final Map<String, String> declarations = new LinkedHashMap<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    public Style() {}

    public static Style of() {
        return new Style();
    }

    public Style set(CssProperty property, String value) {
        return set(Objects.requireNonNull(property, "property").css(), value);
    }

    /**
     * Sets a property by name ({@code "grid-template-columns"}, {@code "--gap"}); null takes it off. A value is one
     * value: no {@code ;}, braces or comments.
     */
    public Style set(String property, String value) {
        Objects.requireNonNull(property, "property");
        if (!PROPERTY.matcher(property).matches()) throw new IllegalArgumentException("not a CSS property's name: " + property);
        String p = property.startsWith("--") ? property : property.toLowerCase(Locale.ROOT);
        if (value != null) check(value);
        String old = value == null ? declarations.remove(p) : declarations.put(p, value.strip());
        if (!Objects.equals(old, value == null ? null : value.strip())) changed();
        return this;
    }

    public Style remove(CssProperty property) {
        return set(property, null);
    }

    public String get(CssProperty property) {
        return declarations.get(property.css());
    }

    public String get(String property) {
        return declarations.get(property);
    }

    public boolean isEmpty() {
        return declarations.isEmpty();
    }

    public Map<String, String> declarations() {
        return Collections.unmodifiableMap(declarations);
    }

    /** Takes all of {@code other}'s declarations (its later ones win). */
    public Style add(Style other) {
        other.declarations.forEach(this::set);
        return this;
    }

    public Style clear() {
        if (!declarations.isEmpty()) {
            declarations.clear();
            changed();
        }
        return this;
    }

    /** As CSS writes it: {@code display: grid; gap: 8px}. */
    public String toCss() {
        StringBuilder b = new StringBuilder();
        declarations.forEach((k, v) -> {
            if (!b.isEmpty()) b.append("; ");
            b.append(k).append(": ").append(v);
        });
        return b.toString();
    }

    @Override
    public String toString() {
        return toCss();
    }

    // ---- values ----

    /** A colour for each scheme: the first in light, the second in dark ({@code light-dark()}). */
    public static String lightDark(String light, String dark) {
        return "light-dark(" + check(light) + ", " + check(dark) + ")";
    }

    /** A custom property's value: {@code var(--gap)}, or {@code var(--gap, 8px)} with a fallback. */
    public static String var(String name, String... fallback) {
        String n = name.startsWith("--") ? name : "--" + name;
        if (!PROPERTY.matcher(n).matches()) throw new IllegalArgumentException("not a custom property's name: " + name);
        return fallback.length == 0 ? "var(" + n + ")" : "var(" + n + ", " + check(fallback[0]) + ")";
    }

    static String check(String value) {
        Objects.requireNonNull(value, "value");
        if (value.indexOf(';') >= 0 || value.indexOf('{') >= 0 || value.indexOf('}') >= 0 || value.contains("/*")
                || value.toLowerCase(Locale.ROOT).contains("</style")) {
            throw new IllegalArgumentException("not one CSS value: " + value);
        }
        return value;
    }

    // ---- who shows it: inside the module (io.joshuasalcedo.swaswa.internal.Access) ----

    static {
        io.joshuasalcedo.swaswa.internal.Access.looks(new io.joshuasalcedo.swaswa.internal.Access.Looks() {
            @Override
            public void listen(Object styleOrSheet, Runnable changed) {
                if (styleOrSheet instanceof Style s) s.listeners.add(changed);
                else ((StyleSheet) styleOrSheet).listen(changed);
            }

            @Override
            public void unlisten(Object styleOrSheet, Runnable changed) {
                if (styleOrSheet instanceof Style s) s.listeners.remove(changed);
                else ((StyleSheet) styleOrSheet).unlisten(changed);
            }
        });
    }

    void listen(Runnable listener) {
        listeners.add(listener);
    }

    void unlisten(Runnable listener) {
        listeners.remove(listener);
    }

    private void changed() {
        for (Runnable r : listeners) r.run();
    }
}
