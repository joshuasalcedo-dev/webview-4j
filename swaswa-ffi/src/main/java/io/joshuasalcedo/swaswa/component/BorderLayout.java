package io.joshuasalcedo.swaswa.component;

import io.joshuasalcedo.swaswa.css.CssProperty;
import io.joshuasalcedo.swaswa.css.Style;

/**
 * Swing's {@code BorderLayout}, JavaFX's {@code BorderPane}: a child at each edge and one in the centre, which takes
 * the room left ({@code display: grid} with named areas). Added without a place, a child goes in the centre.
 * <pre>{@code
 * frame.setLayout(new BorderLayout());
 * frame.add(toolbar, BorderLayout.NORTH);
 * frame.add(editor, BorderLayout.CENTER);
 * }</pre>
 */
public final class BorderLayout implements Layout {

    /** Where a child goes: an edge, or the centre. */
    public enum Region implements Constraint { NORTH, SOUTH, EAST, WEST, CENTER }

    public static final Region NORTH = Region.NORTH;
    public static final Region SOUTH = Region.SOUTH;
    public static final Region EAST = Region.EAST;
    public static final Region WEST = Region.WEST;
    public static final Region CENTER = Region.CENTER;
    /** JavaFX's names for the same places. */
    public static final Region TOP = NORTH, BOTTOM = SOUTH, LEFT = WEST, RIGHT = EAST;

    private final double hgap;
    private final double vgap;

    public BorderLayout() {
        this(0, 0);
    }

    public BorderLayout(double hgap, double vgap) {
        this.hgap = hgap;
        this.vgap = vgap;
    }

    @Override
    public void layOut(Style c) {
        c.set(CssProperty.DISPLAY, "grid")
                .set(CssProperty.GRID_TEMPLATE_AREAS, "\"north north north\" \"west center east\" \"south south south\"")
                .set(CssProperty.GRID_TEMPLATE_COLUMNS, "auto minmax(0, 1fr) auto")
                .set(CssProperty.GRID_TEMPLATE_ROWS, "auto minmax(0, 1fr) auto")
                .set(CssProperty.GAP, vgap + "px " + hgap + "px");
    }

    @Override
    public void place(Style child, Constraint constraint, int index) {
        Region region = switch (constraint) {
            case null -> Region.CENTER;
            case Region r -> r;
            // Swing's names, and JavaFX's
            case Constraint.Named(String name) -> switch (name.toLowerCase(java.util.Locale.ROOT)) {
                case "north", "top", "page_start" -> Region.NORTH;
                case "south", "bottom", "page_end" -> Region.SOUTH;
                case "east", "right", "line_end" -> Region.EAST;
                case "west", "left", "line_start" -> Region.WEST;
                case "center" -> Region.CENTER;
                default -> throw new IllegalArgumentException("a BorderLayout place is North, South, East, West or Center, not " + name);
            };
            default -> throw new IllegalArgumentException("a BorderLayout place is a BorderLayout.Region, not " + constraint);
        };
        child.set(CssProperty.GRID_AREA, region.name().toLowerCase(java.util.Locale.ROOT));
    }
}
