package io.joshuasalcedo.webview4j.ui.components.layout;

import io.joshuasalcedo.webview4j.ui.Component;
import io.joshuasalcedo.webview4j.ui.components.Element;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Children in rows and columns, as JavaFX's {@code GridPane}: each one added at its column and row (from 0), across
 * several if asked. A form of labels and fields is the usual one.
 * <pre>{@code
 * new GridPane().hgap(12).vgap(8)
 *         .columns(ColumnConstraints.fit().halignment(HPos.RIGHT), ColumnConstraints.grow())
 *         .addRow(0, label("Name"), textField("Name"))
 *         .addRow(1, label("Email"), textField("Email"))
 *         .add(button("Save").accent(), 1, 2, 1, 1, HPos.RIGHT, VPos.CENTER)
 * }</pre>
 */
public final class GridPane extends Pane<GridPane> {

    /** How a column is sized, as JavaFX's {@code ColumnConstraints} (rows: the width is read as a height). */
    public record ColumnConstraints(Double width, Double percent, Double weight, Double min, Double max, HPos halignment) {

        /** As wide as its widest child. */
        public static ColumnConstraints fit() { return new ColumnConstraints(null, null, null, null, null, null); }
        /** Fixed, CSS pixels. */
        public static ColumnConstraints fixed(double width) { return new ColumnConstraints(width, null, null, null, null, null); }
        /** A share of the width, 0 to 100. */
        public static ColumnConstraints percent(double percent) { return new ColumnConstraints(null, percent, null, null, null, null); }
        /** Takes the room left over (JavaFX's hgrow ALWAYS). */
        public static ColumnConstraints grow() { return grow(1); }
        /** Takes a share of the room left over, by weight among the columns that grow. */
        public static ColumnConstraints grow(double weight) { return new ColumnConstraints(null, null, weight, null, null, null); }

        public ColumnConstraints min(double min) { return new ColumnConstraints(width, percent, weight, min, max, halignment); }
        public ColumnConstraints max(double max) { return new ColumnConstraints(width, percent, weight, min, max, halignment); }
        /** Where the column's children sit across it. */
        public ColumnConstraints halignment(HPos h) { return new ColumnConstraints(width, percent, weight, min, max, h); }

        Map<String, Object> write() {
            Map<String, Object> out = new LinkedHashMap<>();
            if (width != null) out.put("width", width);
            if (percent != null) out.put("percent", percent);
            if (weight != null) out.put("grow", weight);
            if (min != null) out.put("minWidth", min);
            if (max != null) out.put("maxWidth", max);
            if (halignment != null) out.put("halignment", halignment.key());
            return out;
        }
    }

    public GridPane() {
        super("gridpane");
    }

    public GridPane hgap(double gap) { return prop("hgap", gap); }
    public GridPane vgap(double gap) { return prop("vgap", gap); }

    /** Both gaps at once. */
    public GridPane gap(double gap) {
        hgap(gap);
        return vgap(gap);
    }

    /** How each column is sized, in order. */
    public GridPane columns(ColumnConstraints... columns) {
        List<Object> out = new ArrayList<>();
        for (ColumnConstraints c : columns) out.add(c.write());
        return prop("columns", out);
    }

    /** How each row is sized, in order (the width read as a height). */
    public GridPane rows(ColumnConstraints... rows) {
        List<Object> out = new ArrayList<>();
        for (ColumnConstraints r : rows) out.add(r.write());
        return prop("rows", out);
    }

    /** A child at a column and row, from 0 (JavaFX's {@code add(node, column, row)}). */
    public GridPane add(Component<?> child, int column, int row) {
        return add(child, column, row, 1, 1);
    }

    /** A child across {@code columnSpan} columns and {@code rowSpan} rows. */
    public GridPane add(Component<?> child, int column, int row, int columnSpan, int rowSpan) {
        return add(child, column, row, columnSpan, rowSpan, null, null);
    }

    /** A child placed in its cell: left, center or right; top, center or bottom (null: as the column says). */
    public GridPane add(Component<?> child, int column, int row, int columnSpan, int rowSpan, HPos halignment, VPos valignment) {
        Element cell = new Element("gridCell").set("column", column).set("row", row)
                .set("columnSpan", columnSpan).set("rowSpan", rowSpan).with(child);
        if (halignment != null) cell.set("halignment", halignment.key());
        if (valignment != null) cell.set("valignment", valignment.key());
        return add(cell);
    }

    /** A row of children, one per column from the first (JavaFX's {@code addRow}). */
    public GridPane addRow(int row, Component<?>... children) {
        for (int c = 0; c < children.length; c++) add(children[c], c, row);
        return this;
    }

    /** A column of children, one per row from the first (JavaFX's {@code addColumn}). */
    public GridPane addColumn(int column, Component<?>... children) {
        for (int r = 0; r < children.length; r++) add(children[r], column, r);
        return this;
    }
}
