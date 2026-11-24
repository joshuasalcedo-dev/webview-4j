package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** A scrolling area that asks for more before its end is reached. */
public final class InfiniteScroll extends Component<InfiniteScroll> {

    public InfiniteScroll(Component<?>... children) {
        super("infiniteScroll");
        add(children);
    }

    /** Whether there is more to load. */
    public InfiniteScroll hasMore(boolean more) { return prop("hasMore", more); }
    /** "up": for histories that grow upwards, like a chat's. */
    public InfiniteScroll upwards() { return prop("direction", "up"); }
    public InfiniteScroll endText(String text) { return prop("endText", text); }
    /** Loads the next part (the screen shows again with it). */
    public InfiniteScroll onLoadMore(Runnable action) { return on("onLoadMore", v -> action.run()); }
    public InfiniteScroll with(Component<?>... children) { return add(children); }
    public InfiniteScroll with(java.util.List<? extends Component<?>> children) { return add(children); }
}
