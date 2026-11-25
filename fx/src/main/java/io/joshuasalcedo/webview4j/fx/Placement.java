package io.joshuasalcedo.webview4j.fx;

import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;

/**
 * Where a docked webview goes: the node's place on the screen, cut to what of it can be seen (its window's scene, and
 * every clip above it, such as a scroll pane's viewport), in physical pixels.
 */
record Placement(int x, int y, int width, int height) {

    /** Null when nothing of the node can be seen. */
    static Placement of(Bounds node, Bounds visible, double scaleX, double scaleY) {
        double minX = Math.max(node.getMinX(), visible.getMinX());
        double minY = Math.max(node.getMinY(), visible.getMinY());
        double maxX = Math.min(node.getMaxX(), visible.getMaxX());
        double maxY = Math.min(node.getMaxY(), visible.getMaxY());
        int x = (int) Math.round(minX * scaleX);
        int y = (int) Math.round(minY * scaleY);
        int width = (int) Math.round(maxX * scaleX) - x;
        int height = (int) Math.round(maxY * scaleY) - y;
        return width < 1 || height < 1 ? null : new Placement(x, y, width, height);
    }

    /** The part both show; an empty box when they do not meet. */
    static Bounds intersect(Bounds a, Bounds b) {
        double minX = Math.max(a.getMinX(), b.getMinX());
        double minY = Math.max(a.getMinY(), b.getMinY());
        double maxX = Math.min(a.getMaxX(), b.getMaxX());
        double maxY = Math.min(a.getMaxY(), b.getMaxY());
        return new BoundingBox(minX, minY, Math.max(0, maxX - minX), Math.max(0, maxY - minY));
    }
}
