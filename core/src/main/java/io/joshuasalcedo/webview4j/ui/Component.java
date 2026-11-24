package io.joshuasalcedo.webview4j.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A part of a screen built in Java: one of {@code @webview4j/ui}'s components, with its properties, its children
 * and the Java code that runs when the user acts on it. Made with {@link Ui}'s methods; a {@link Screen} returns
 * a tree of them from {@code render()}, and the window shows it with the real components.
 *
 * @param <C> the component's own type, so its settings chain: {@code button("Save").accent().onClick(...)}
 */
public abstract class Component<C extends Component<C>> {

    private final String type;
    final Map<String, Object> props = new LinkedHashMap<>();
    final List<Component<?>> children = new ArrayList<>();
    final Map<String, Consumer<Object>> handlers = new LinkedHashMap<>();
    private String key;

    Component(String type) {
        this.type = Objects.requireNonNull(type, "type");
    }

    @SuppressWarnings("unchecked")
    C self() {
        return (C) this;
    }

    /**
     * What tells this component apart from its siblings, when a list of them changes (an id): so each keeps what
     * the user typed in it, its scroll and its focus. Without one, its place among its siblings.
     */
    public C key(Object key) {
        this.key = String.valueOf(Objects.requireNonNull(key, "key"));
        return self();
    }

    /** Takes the room left in its row or column. */
    public C grow() {
        return prop("grow", true);
    }

    /** A width in CSS pixels. */
    public C width(double width) {
        return prop("width", width);
    }

    /** A height in CSS pixels. */
    public C height(double height) {
        return prop("height", height);
    }

    C prop(String name, Object value) {
        if (value == null) props.remove(name);
        else props.put(name, value);
        return self();
    }

    C on(String event, Consumer<Object> handler) {
        handlers.put(event, Objects.requireNonNull(handler, "handler"));
        return self();
    }

    C add(Component<?>... components) {
        for (Component<?> c : components) if (c != null) children.add(c);
        return self();
    }

    C add(List<? extends Component<?>> components) {
        for (Component<?> c : components) if (c != null) children.add(c);
        return self();
    }

    /**
     * The tree as the page's renderer reads it: {@code {"t": type, "p": props, "c": children, "k": key}}, each
     * handler written as an id ({@code "h3"}) that {@code handlers} maps to the Java code.
     */
    Map<String, Object> toTree(Map<String, Consumer<Object>> registry) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("t", type);
        Map<String, Object> p = new LinkedHashMap<>(props);
        for (Map.Entry<String, Consumer<Object>> handler : handlers.entrySet()) {
            String id = "h" + (registry.size() + 1);
            registry.put(id, handler.getValue());
            p.put(handler.getKey(), id);
        }
        node.put("p", p);
        if (!children.isEmpty()) {
            List<Object> c = new ArrayList<>(children.size());
            for (Component<?> child : children) c.add(child.toTree(registry));
            node.put("c", c);
        }
        if (key != null) node.put("k", key);
        return node;
    }
}
