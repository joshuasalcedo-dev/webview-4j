package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;
import java.util.function.Consumer;

/** A part of a component the renderer knows by its type (a tab, a crumb, a dialog's button), or one it has no class for. */
public final class Element extends Component<Element> {

    public Element(String type) {
        super(type);
    }

    public Element set(String name, Object value) {
        return prop(name, value);
    }

    public Element handle(String event, Consumer<Object> handler) {
        return on(event, handler);
    }

    public Element with(Component<?>... children) {
        return add(children);
    }
}
