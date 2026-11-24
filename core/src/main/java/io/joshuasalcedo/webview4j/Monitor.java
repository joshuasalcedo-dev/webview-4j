package io.joshuasalcedo.webview4j;

/**
 * A monitor, in the same logical pixels as a window's {@link Geometry} (so the two compare): where it is on the
 * desktop, its size, its scale, and whether it is the main one and the one the window is on.
 */
public record Monitor(String name, double x, double y, double width, double height, double scaleFactor, boolean primary,
                      boolean current) {

    /** How much of a window with these bounds is on this monitor, 0 to 1. */
    public double covers(double wx, double wy, double ww, double wh) {
        double w = Math.max(0, Math.min(x + width, wx + ww) - Math.max(x, wx));
        double h = Math.max(0, Math.min(y + height, wy + wh) - Math.max(y, wy));
        double area = ww * wh;
        return area <= 0 ? 0 : (w * h) / area;
    }
}
