package io.joshuasalcedo.webview4j.ui.components.layout;

import io.joshuasalcedo.webview4j.ui.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Panes with dividers between them to drag, as JavaFX's {@code SplitPane}: side by side or one over the other, as
 * many as given. Where the dividers are (0 to 1 from the start) comes back when one is let go.
 * <pre>{@code
 * new SplitPane(navigator, new SplitPane(editor, console).orientation(SplitPane.Orientation.VERTICAL))
 *         .dividerPositions(split).onDividerPositionsChange(p -> split = p)
 * }</pre>
 */
public final class SplitPane extends Pane<SplitPane> {

    /** Side by side, or one over the other. */
    public enum Orientation { HORIZONTAL, VERTICAL }

    public SplitPane(Component<?>... panes) {
        super("splitpane");
        addAll(panes);
    }

    public SplitPane orientation(Orientation orientation) {
        return prop("orientation", orientation == Orientation.VERTICAL ? "vertical" : "horizontal");
    }

    /** Where the dividers are, 0 to 1 from the start: one fewer than the panes. Evenly unless given. */
    public SplitPane dividerPositions(double... positions) {
        List<Double> out = new ArrayList<>();
        for (double p : positions) out.add(Math.max(0, Math.min(1, p)));
        return prop("dividerPositions", out);
    }

    /** The smallest a pane gets, CSS pixels (48 unless set). */
    public SplitPane minPaneSize(double size) {
        return prop("minSize", size);
    }

    /** A divider was let go: where they all are now. */
    public SplitPane onDividerPositionsChange(Consumer<double[]> action) {
        return on("onDividerPositionsChange", v -> {
            List<?> list = v instanceof List<?> l ? l : List.of();
            double[] positions = new double[list.size()];
            for (int i = 0; i < positions.length; i++) positions[i] = list.get(i) instanceof Number n ? n.doubleValue() : 0;
            action.accept(positions);
        });
    }
}
