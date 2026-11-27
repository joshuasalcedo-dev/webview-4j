package io.joshuasalcedo.swaswa.component;

import io.joshuasalcedo.swaswa.css.CssProperty;
import io.joshuasalcedo.swaswa.css.Style;

/**
 * Swing's {@code BoxLayout}, JavaFX's {@code HBox} and {@code VBox}: in a row or a column, each its own size
 * ({@code display: flex}). A child added with {@link #GROW} takes the room left.
 */
public final class BoxLayout implements Layout {

    public enum Axis { X_AXIS, Y_AXIS }

    /** A child's constraint: it takes the room the others leave (JavaFX's {@code Priority.ALWAYS}). */
    public static final Constraint GROW = new Constraint() {
        @Override public String toString() { return "grow"; }
    };

    private final Axis axis;
    private final double gap;
    private final String alignment;

    public BoxLayout(Axis axis) {
        this(axis, 0, "stretch");
    }

    private BoxLayout(Axis axis, double gap, String alignment) {
        this.axis = java.util.Objects.requireNonNull(axis, "axis");
        this.gap = gap;
        this.alignment = alignment;
    }

    /** JavaFX's {@code HBox}: in a row, {@code spacing} apart. */
    public static BoxLayout row(double spacing) {
        return new BoxLayout(Axis.X_AXIS, spacing, "center");
    }

    /** JavaFX's {@code VBox}: in a column, {@code spacing} apart. */
    public static BoxLayout column(double spacing) {
        return new BoxLayout(Axis.Y_AXIS, spacing, "stretch");
    }

    /** How they line up across it: CSS's {@code align-items} ({@code start}, {@code center}, {@code end}, {@code stretch}). */
    public BoxLayout aligned(String alignItems) {
        return new BoxLayout(axis, gap, Style.of().set(CssProperty.ALIGN_ITEMS, alignItems).get(CssProperty.ALIGN_ITEMS));
    }

    @Override
    public void layOut(Style c) {
        c.set(CssProperty.DISPLAY, "flex").set(CssProperty.FLEX_DIRECTION, axis == Axis.X_AXIS ? "row" : "column")
                .set(CssProperty.ALIGN_ITEMS, alignment).set(CssProperty.GAP, gap + "px");
    }

    @Override
    public void place(Style child, Constraint constraint, int index) {
        if (constraint == GROW) child.set(CssProperty.FLEX, "1 1 0").set(CssProperty.MIN_WIDTH, "0").set(CssProperty.MIN_HEIGHT, "0");
    }
}
