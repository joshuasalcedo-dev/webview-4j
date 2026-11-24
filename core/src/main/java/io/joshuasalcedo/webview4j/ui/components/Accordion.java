package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;
import java.util.function.IntConsumer;

/**
 * Sections where one is open at a time, as JavaFX's {@code Accordion}: opening one closes the other.
 * <pre>{@code
 * new Accordion().pane("General", general).pane("Privacy", privacy).pane("About", about)
 *         .expanded(open).onExpandedChange(i -> open = i)
 * }</pre>
 */
public final class Accordion extends Component<Accordion> {

    public Accordion() {
        super("accordion");
    }

    /** A section: its title and what is in it. */
    public Accordion pane(String title, Component<?>... content) {
        return add(new Element("accordionPane").set("title", title).with(content));
    }

    /** The open one, from 0; -1 for none. */
    public Accordion expanded(int index) { return prop("expanded", index); }
    public Accordion onExpandedChange(IntConsumer action) { return on("onExpandedChange", v -> action.accept(v instanceof Number n ? n.intValue() : -1)); }
}
