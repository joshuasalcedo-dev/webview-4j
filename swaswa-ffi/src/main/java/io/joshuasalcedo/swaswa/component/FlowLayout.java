package io.joshuasalcedo.swaswa.component;

import io.joshuasalcedo.swaswa.css.CssProperty;
import io.joshuasalcedo.swaswa.css.Style;

/**
 * Swing's {@code FlowLayout}: one after another in a row, onto the next row when the row is full
 * ({@code display: flex; flex-wrap: wrap}).
 */
public final class FlowLayout implements Layout {

    public enum Align { LEADING, CENTER, TRAILING }

    private final Align align;
    private final double hgap;
    private final double vgap;

    /** Centred, 5 pixels apart, as Swing's. */
    public FlowLayout() {
        this(Align.CENTER, 5, 5);
    }

    public FlowLayout(Align align) {
        this(align, 5, 5);
    }

    public FlowLayout(Align align, double hgap, double vgap) {
        this.align = java.util.Objects.requireNonNull(align, "align");
        this.hgap = hgap;
        this.vgap = vgap;
    }

    @Override
    public void layOut(Style c) {
        c.set(CssProperty.DISPLAY, "flex").set(CssProperty.FLEX_WRAP, "wrap").set(CssProperty.ALIGN_ITEMS, "center")
                .set(CssProperty.JUSTIFY_CONTENT, switch (align) { case LEADING -> "flex-start"; case CENTER -> "center"; case TRAILING -> "flex-end"; })
                .set(CssProperty.GAP, vgap + "px " + hgap + "px");
    }
}
