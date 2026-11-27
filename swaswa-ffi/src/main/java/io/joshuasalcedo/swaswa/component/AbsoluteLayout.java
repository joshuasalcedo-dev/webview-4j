package io.joshuasalcedo.swaswa.component;

import io.joshuasalcedo.swaswa.css.CssProperty;
import io.joshuasalcedo.swaswa.css.Style;

/**
 * Swing's {@code null} layout, JavaFX's {@code Pane}: each where it is told, in CSS pixels from the container's top
 * left ({@code position: absolute}). Added with no place, a child sits at the top left.
 */
public final class AbsoluteLayout implements Layout {

    /** A child's place and size; a size of 0 or less: its own. */
    public record Bounds(double x, double y, double width, double height) implements Constraint {
        public static Bounds at(double x, double y) {
            return new Bounds(x, y, 0, 0);
        }
    }

    @Override
    public void layOut(Style c) {
        c.set(CssProperty.POSITION, "relative");
    }

    @Override
    public void place(Style child, Constraint constraint, int index) {
        Bounds b = constraint instanceof Bounds given ? given : Bounds.at(0, 0);
        if (constraint != null && !(constraint instanceof Bounds)) throw new IllegalArgumentException("an AbsoluteLayout place is Bounds, not " + constraint);
        child.set(CssProperty.POSITION, "absolute").set(CssProperty.LEFT, b.x() + "px").set(CssProperty.TOP, b.y() + "px");
        if (b.width() > 0) child.set(CssProperty.WIDTH, b.width() + "px");
        if (b.height() > 0) child.set(CssProperty.HEIGHT, b.height() + "px");
    }
}
