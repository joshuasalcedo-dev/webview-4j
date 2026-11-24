package io.joshuasalcedo.webview4j.ui.components.layout;

import io.joshuasalcedo.webview4j.ui.Component;
import java.util.Locale;

/**
 * A child bigger than its room, scrolled, as JavaFX's {@code ScrollPane}. As wide as the pane and scrolled up and
 * down unless told otherwise.
 * <pre>{@code
 * new ScrollPane(longForm).height(400)
 * }</pre>
 */
public final class ScrollPane extends Pane<ScrollPane> {

    /** When a scroll bar shows, as JavaFX's {@code ScrollBarPolicy}. */
    public enum ScrollBarPolicy { AS_NEEDED, ALWAYS, NEVER }

    public ScrollPane(Component<?> content) {
        super("scrollpane");
        add(content);
    }

    /** The child as wide as the pane: scrolled only up and down (on unless set). */
    public ScrollPane fitToWidth(boolean fit) { return prop("fitToWidth", fit); }
    /** The child as tall as the pane: scrolled only sideways. */
    public ScrollPane fitToHeight(boolean fit) { return prop("fitToHeight", fit); }

    public ScrollPane hbarPolicy(ScrollBarPolicy policy) { return prop("hbarPolicy", key(policy)); }
    public ScrollPane vbarPolicy(ScrollBarPolicy policy) { return prop("vbarPolicy", key(policy)); }

    private static String key(ScrollBarPolicy policy) {
        return policy.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
