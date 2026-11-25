package io.joshuasalcedo.webview4j.ui;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * How a component looks beyond what it offers itself: spacing, colours, border, corners, type. Given with
 * {@link Component#style(Style)}; for anything else, {@link Component#css(String, String)}.
 * <pre>{@code
 * surface("Totals", ...).style(Style.of()
 *         .padding(16).margin(0, 0, 12, 0)
 *         .background(Style.Token.ACCENT_SUBTLE).color(Style.Token.TEXT)
 *         .border(1, Style.Token.BORDER).radius(8)
 *         .fontWeight(600))
 * }</pre>
 * Colours are the kit's tokens, so they follow light and dark and the platform's look; a raw CSS colour
 * ({@code "#7c3aed"}) works too but stays the same in both.
 */
public final class Style {

    /** The kit's colours, as CSS variables that change with light and dark and with Windows' or macOS's look. */
    public enum Token {
        /** The window's background. */
        WINDOW("--nt-window"),
        /** A card's or a panel's background. */
        SURFACE("--nt-surface"),
        TEXT("--nt-text"),
        TEXT_SECONDARY("--nt-text-secondary"),
        TEXT_DISABLED("--nt-text-disabled"),
        /** The system's accent (Windows' blue, or the frame's accent). */
        ACCENT("--nt-accent"),
        /** Text on the accent. */
        ON_ACCENT("--nt-on-accent"),
        /** A faint wash: a hover, a selected row. */
        ACCENT_SUBTLE("--nt-subtle-hover"),
        BORDER("--nt-control-border-bottom"),
        ERROR("--nt-error"),
        CHART_1("--nt-chart-1"), CHART_2("--nt-chart-2"), CHART_3("--nt-chart-3"), CHART_4("--nt-chart-4"),
        CHART_5("--nt-chart-5"), CHART_6("--nt-chart-6"), CHART_7("--nt-chart-7"), CHART_8("--nt-chart-8");

        final String variable;

        Token(String variable) { this.variable = variable; }

        String css() { return "var(" + variable + ")"; }
    }

    private static final Pattern PROPERTY = Pattern.compile("--[a-zA-Z0-9-]+|[a-z]+(-[a-z]+)*");
    private static final Pattern BAD_VALUE = Pattern.compile("[;{}<>\\\\]|/\\*|(?i)expression\\s*\\(|(?i)javascript:|(?i)url\\s*\\(\\s*['\"]?\\s*(?!https?:|data:image/|app/|/)");

    private final Map<String, String> declarations = new LinkedHashMap<>();

    private Style() {}

    public static Style of() {
        return new Style();
    }

    // --- spacing ---

    /** Space inside, all round, in px. */
    public Style padding(double all) { return set("padding", px(all)); }
    /** Space inside: top and bottom, left and right, in px. */
    public Style padding(double vertical, double horizontal) { return set("padding", px(vertical) + " " + px(horizontal)); }
    public Style padding(double top, double right, double bottom, double left) { return set("padding", px(top) + " " + px(right) + " " + px(bottom) + " " + px(left)); }
    /** Space outside, all round, in px. */
    public Style margin(double all) { return set("margin", px(all)); }
    public Style margin(double vertical, double horizontal) { return set("margin", px(vertical) + " " + px(horizontal)); }
    public Style margin(double top, double right, double bottom, double left) { return set("margin", px(top) + " " + px(right) + " " + px(bottom) + " " + px(left)); }

    // --- colour ---

    public Style background(Token token) { return set("background", token.css()); }
    /** A CSS colour or background ({@code "#f5f5f5"}, {@code "linear-gradient(...)"}): the same in light and dark. */
    public Style background(String css) { return set("background", css); }
    /** The text's colour. */
    public Style color(Token token) { return set("color", token.css()); }
    public Style color(String css) { return set("color", css); }
    /** 0 (invisible) to 1. */
    public Style opacity(double opacity) {
        if (opacity < 0 || opacity > 1) throw new IllegalArgumentException("opacity is 0 to 1: " + opacity);
        return set("opacity", number(opacity));
    }

    // --- border and corners ---

    /** A solid border, {@code width} px, in a token's colour. */
    public Style border(double width, Token color) { return set("border", px(width) + " solid " + color.css()); }
    public Style border(double width, String color) { return set("border", px(width) + " solid " + color); }
    /** Rounded corners, in px. */
    public Style radius(double px) { return set("border-radius", px(px)); }
    /** A shadow under it: 0 none, 1 slight, 2 a card's, 3 a dialog's. */
    public Style elevation(int level) {
        return set("box-shadow", switch (level) {
            case 0 -> "none";
            case 1 -> "0 1px 3px rgba(0,0,0,.12)";
            case 2 -> "0 4px 14px rgba(0,0,0,.14)";
            case 3 -> "0 12px 36px rgba(0,0,0,.22)";
            default -> throw new IllegalArgumentException("elevation is 0 to 3: " + level);
        });
    }

    // --- type ---

    /** The text's size, in px. */
    public Style fontSize(double px) { return set("font-size", px(px)); }
    /** 100 (thin) to 900 (black): 400 regular, 600 semibold, 700 bold. */
    public Style fontWeight(int weight) {
        if (weight < 100 || weight > 900 || weight % 100 != 0) throw new IllegalArgumentException("fontWeight is 100, 200 ... 900: " + weight);
        return set("font-weight", Integer.toString(weight));
    }
    public Style italic() { return set("font-style", "italic"); }
    /** left, center, right, justify. */
    public Style textAlign(String align) {
        if (!align.matches("left|center|right|justify|start|end")) throw new IllegalArgumentException("textAlign: left, center, right, justify: " + align);
        return set("text-align", align);
    }

    // --- anything else ---

    /**
     * Any CSS property: {@code set("letter-spacing", "0.02em")}, a custom property ({@code "--my-gap"}). Values
     * cannot hold {@code ; { } < >}, comments, {@code expression()} or a {@code javascript:} or foreign url().
     */
    public Style set(String property, String value) {
        Objects.requireNonNull(property, "property");
        Objects.requireNonNull(value, "value");
        String name = property.strip();
        if (!PROPERTY.matcher(name).matches()) throw new IllegalArgumentException("not a CSS property: " + property);
        String v = value.strip();
        if (v.isEmpty() || BAD_VALUE.matcher(v).find()) throw new IllegalArgumentException("not a safe CSS value for " + name + ": " + value);
        declarations.put(name, v);
        return this;
    }

    /** The declarations, in order, as {@code property → value}. */
    public Map<String, String> declarations() {
        return Collections.unmodifiableMap(declarations);
    }

    private static String px(double value) {
        return number(value) + "px";
    }

    private static String number(double value) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("not a number: " + value);
        return value == Math.rint(value) ? Long.toString((long) value) : String.format(Locale.ROOT, "%s", value);
    }
}
