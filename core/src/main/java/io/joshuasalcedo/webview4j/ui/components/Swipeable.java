package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** Something a finger or the trackpad swipes aside to reveal actions. */
public final class Swipeable extends Component<Swipeable> {

    public Swipeable(Component<?> content) {
        super("swipeable");
        add(slot(content, "content"));
    }

    /** An action revealed by swiping right; {@code tone}: accent, success, warning, error or neutral. */
    public Swipeable right(String label, String tone, Runnable action) { return side("right", label, tone, action); }
    public Swipeable left(String label, String tone, Runnable action) { return side("left", label, tone, action); }

    private Swipeable side(String side, String label, String tone, Runnable action) {
        return add(slot(new Element("swipeAction").set("label", label).set("tone", tone).handle("onAction", v -> action.run()), side));
    }
}
