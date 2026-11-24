package io.joshuasalcedo.webview4j.ui.components.layout;

import io.joshuasalcedo.webview4j.ui.Component;
import io.joshuasalcedo.webview4j.ui.components.Element;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Children pinned to its edges by offsets, as JavaFX's {@code AnchorPane}: pinned to both left and right (or top and
 * bottom), a child stretches between them.
 * <pre>{@code
 * new AnchorPane()
 *         .add(logo, AnchorPane.Anchors.topLeft(8, 8))
 *         .add(closeButton, AnchorPane.Anchors.topRight(8, 8))
 *         .add(footer, new AnchorPane.Anchors(null, 8.0, 8.0, 8.0))
 * }</pre>
 */
public final class AnchorPane extends Pane<AnchorPane> {

    /** How far from each edge a child is pinned, CSS pixels; null: not pinned to that edge. */
    public record Anchors(Double top, Double right, Double bottom, Double left) {
        public static Anchors topLeft(double top, double left) { return new Anchors(top, null, null, left); }
        public static Anchors topRight(double top, double right) { return new Anchors(top, right, null, null); }
        public static Anchors bottomLeft(double bottom, double left) { return new Anchors(null, null, bottom, left); }
        public static Anchors bottomRight(double bottom, double right) { return new Anchors(null, right, bottom, null); }
        /** Pinned to every edge: it fills the pane but for the offset. */
        public static Anchors fill(double offset) { return new Anchors(offset, offset, offset, offset); }

        Map<String, Object> write() {
            Map<String, Object> out = new LinkedHashMap<>();
            if (top != null) out.put("top", top);
            if (right != null) out.put("right", right);
            if (bottom != null) out.put("bottom", bottom);
            if (left != null) out.put("left", left);
            return out;
        }
    }

    public AnchorPane() {
        super("anchorpane");
    }

    /** A child pinned where {@code anchors} say (JavaFX's setTopAnchor and the others). */
    public AnchorPane add(Component<?> child, Anchors anchors) {
        return add(new Element("anchored").set("anchors", anchors.write()).with(child));
    }
}
