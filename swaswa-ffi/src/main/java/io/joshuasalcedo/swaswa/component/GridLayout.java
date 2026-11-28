package io.joshuasalcedo.swaswa.component;

import io.joshuasalcedo.swaswa.css.CssProperty;
import io.joshuasalcedo.swaswa.css.Style;

/**
 * Swing's {@code GridLayout} (rows and columns of one size) and JavaFX's {@code GridPane} (a child at a cell, spanning
 * some): {@code display: grid}.
 * <pre>{@code
 * form.setLayout(new GridLayout(0, 2, 8, 8));       // two columns, as many rows as it takes
 * form.add(notes, GridLayout.cell(0, 3).span(2, 1)); // a cell of its own, across both columns
 * }</pre>
 */
public final class GridLayout implements Layout {

    /** A child's place: its column and row (from 0), and how many it spans. */
    public record Cell(int column, int row, int columnSpan, int rowSpan) implements Constraint {
        public Cell {
            if (column < 0 || row < 0 || columnSpan < 1 || rowSpan < 1) throw new IllegalArgumentException("a cell from 0, spanning at least 1");
        }

        public Cell span(int columns, int rows) {
            return new Cell(column, row, columns, rows);
        }
    }

    public static Cell cell(int column, int row) {
        return new Cell(column, row, 1, 1);
    }

    private final int rows;
    private final int columns;
    private final double hgap;
    private final double vgap;

    /** {@code rows} by {@code columns}; 0 for either: as many as it takes. */
    public GridLayout(int rows, int columns) {
        this(rows, columns, 0, 0);
    }

    public GridLayout(int rows, int columns, double hgap, double vgap) {
        if (rows < 0 || columns < 0 || (rows == 0 && columns == 0)) throw new IllegalArgumentException("rows or columns, at least one more than 0");
        this.rows = rows;
        this.columns = columns;
        this.hgap = hgap;
        this.vgap = vgap;
    }

    @Override
    public void layOut(Style c) {
        c.set(CssProperty.DISPLAY, "grid").set(CssProperty.GAP, vgap + "px " + hgap + "px");
        if (columns > 0) c.set(CssProperty.GRID_TEMPLATE_COLUMNS, "repeat(" + columns + ", minmax(0, 1fr))");
        else c.set(CssProperty.GRID_AUTO_FLOW, "column");
        if (rows > 0) c.set(CssProperty.GRID_TEMPLATE_ROWS, "repeat(" + rows + ", minmax(0, 1fr))");
    }

    @Override
    public void place(Style child, Constraint constraint, int index) {
        if (constraint instanceof Cell cell) {
            child.set(CssProperty.GRID_COLUMN, (cell.column() + 1) + " / span " + cell.columnSpan())
                    .set(CssProperty.GRID_ROW, (cell.row() + 1) + " / span " + cell.rowSpan());
        } else if (constraint != null) {
            throw new IllegalArgumentException("a GridLayout place is a GridLayout.cell(...), not " + constraint);
        }
    }
}
