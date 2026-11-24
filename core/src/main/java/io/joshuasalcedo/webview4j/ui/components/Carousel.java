package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;
import java.util.function.IntConsumer;

/**
 * Pages to go through one at a time: arrows at the sides, a dot per page, swipes and the arrow keys; it can move on by
 * itself. A product's pictures, a welcome tour. The Java side of the kit's {@code Carousel}.
 * <pre>{@code
 * new Carousel("Pictures", picture1, picture2, picture3).interval(4000).height(240)
 * }</pre>
 */
public final class Carousel extends Component<Carousel> {

    public Carousel(String label, Component<?>... pages) {
        super("carousel");
        prop("label", label);
        add(pages);
    }

    /** The page shown, from 0. */
    public Carousel index(int index) { return prop("index", index); }
    /** The person went to another page: which. */
    public Carousel onIndexChange(IntConsumer action) { return on("onIndexChange", v -> action.accept(v instanceof Number n ? n.intValue() : 0)); }
    /** Moves on by itself every this many milliseconds (it stops while the pointer is over it). */
    public Carousel interval(int milliseconds) { return prop("interval", milliseconds); }
    /** Stops at the last page instead of going on to the first. */
    public Carousel noLoop() { return prop("loop", false); }
    public Carousel arrows(boolean shown) { return prop("arrows", shown); }
    public Carousel dots(boolean shown) { return prop("dots", shown); }
}
