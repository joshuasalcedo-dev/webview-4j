package io.joshuasalcedo.webview4j.ui.state;

import java.util.Objects;

/**
 * A value the context carries down to a component's children: its name and type. Compared by identity, so keep each
 * in a constant.
 * <pre>{@code
 * static final ContextProperty<User> USER = ContextProperty.of("user");
 * static final ContextProperty<String> ACCENT = ContextProperty.of("accent", "blue");   // blue where none is provided
 *
 * session.provide(USER, signedIn);
 * User me = Context.get(USER);
 * }</pre>
 */
public final class ContextProperty<T> {

    private final String name;
    private final T fallback;

    private ContextProperty(String name, T fallback) {
        this.name = Objects.requireNonNull(name, "name");
        this.fallback = fallback;
    }

    public static <T> ContextProperty<T> of(String name) {
        return new ContextProperty<>(name, null);
    }

    /** With a value for where none was provided. */
    public static <T> ContextProperty<T> of(String name, T fallback) {
        return new ContextProperty<>(name, fallback);
    }

    public String name() {
        return name;
    }

    T fallback() {
        return fallback;
    }

    @Override
    public String toString() {
        return "ContextProperty[" + name + "]";
    }
}
