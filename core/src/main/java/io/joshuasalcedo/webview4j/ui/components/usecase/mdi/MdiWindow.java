package io.joshuasalcedo.webview4j.ui.components.usecase.mdi;

import io.joshuasalcedo.webview4j.ui.Component;

/** One window of an {@link Mdi}: an id that stays the same, its title, and what is inside. */
public final class MdiWindow extends Component<MdiWindow> {

    public MdiWindow(String id, String title, Component<?>... children) {
        super("mdiWindow");
        prop("id", id);
        prop("title", title);
        add(children); key(id);
    }

    /** Where it opens and its size, CSS pixels within the area. */
    public MdiWindow bounds(double x, double y, double width, double height) {
        return prop("x", x).prop("y", y).prop("width", width).prop("height", height);
    }

    public MdiWindow with(Component<?>... children) { return add(children); }
    /** Its close button: without it, closing just hides it until the next render. */
    public MdiWindow onClose(Runnable action) { return on("onClose", v -> action.run()); }
}
