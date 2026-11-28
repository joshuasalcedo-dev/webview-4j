package io.joshuasalcedo.swaswa.component;

import io.joshuasalcedo.swaswa.css.CssProperty;
import io.joshuasalcedo.swaswa.css.Style;

/**
 * JavaFX's {@code StackPane}: one over another, all in the same place, the last on top (each in the one grid cell).
 */
public final class StackLayout implements Layout {

    private final String alignment;

    public StackLayout() {
        this("center");
    }

    /** Where each sits in it: CSS's {@code place-items} ({@code center}, {@code start}, {@code end start}...). */
    public StackLayout(String placeItems) {
        this.alignment = Style.of().set(CssProperty.PLACE_ITEMS, placeItems).get(CssProperty.PLACE_ITEMS);
    }

    @Override
    public void layOut(Style c) {
        c.set(CssProperty.DISPLAY, "grid").set(CssProperty.PLACE_ITEMS, alignment);
    }

    @Override
    public void place(Style child, Constraint constraint, int index) {
        child.set(CssProperty.GRID_AREA, "1 / 1");
    }
}
