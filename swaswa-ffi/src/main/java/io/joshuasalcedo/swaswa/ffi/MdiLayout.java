package io.joshuasalcedo.swaswa.ffi;

import java.util.ArrayList;
import java.util.List;

/**
 * How a window's MDI children are laid out at once ({@link View#arrange}), as a desktop app's Window menu does it.
 */
public enum MdiLayout {
    /** Each a little below and right of the one before, all the same size, the last on top. */
    CASCADE,
    /** Rows and columns of the same size, as square as their number allows. */
    GRID,
    /** One above the other, each the full width ("tile horizontally"). */
    ROWS,
    /** Side by side, each the full height ("tile vertically"). */
    COLUMNS;

    /** The step between cascaded children: enough to show each one's title bar. */
    static final double STEP = 30;

    /**
     * Where {@code count} children go in an area {@code width} by {@code height} at {@code x}, {@code y}: one
     * {x, y, width, height} each, in order.
     */
    List<double[]> places(int count, double x, double y, double width, double height) {
        List<double[]> out = new ArrayList<>(count);
        if (count <= 0) return out;
        switch (this) {
            case CASCADE -> {
                double w = Math.max(160, width * 0.6), h = Math.max(120, height * 0.6);
                int fit = Math.max(1, (int) Math.min((width - w) / STEP, (height - h) / STEP) + 1);   // steps before it wraps
                for (int i = 0; i < count; i++) {
                    int step = i % fit;
                    double shift = (i / fit) * STEP * 0.5;   // a wrapped round starts a little right
                    out.add(new double[] {x + step * STEP + shift, y + step * STEP, Math.min(w, width), Math.min(h, height)});
                }
            }
            case GRID -> {
                int columns = (int) Math.ceil(Math.sqrt(count));
                int rows = (int) Math.ceil(count / (double) columns);
                double h = height / rows;
                for (int i = 0; i < count; i++) {
                    int row = i / columns;
                    int inRow = row == rows - 1 ? count - row * columns : columns;   // the last row's share the width
                    double w = width / inRow;
                    out.add(new double[] {x + (i % columns) * w, y + row * h, w, h});
                }
            }
            case ROWS -> {
                double h = height / count;
                for (int i = 0; i < count; i++) out.add(new double[] {x, y + i * h, width, h});
            }
            case COLUMNS -> {
                double w = width / count;
                for (int i = 0; i < count; i++) out.add(new double[] {x + i * w, y, w, height});
            }
        }
        return out;
    }
}
