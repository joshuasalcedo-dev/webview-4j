package io.joshuasalcedo.swaswa.state;

import io.joshuasalcedo.swaswa.component.ClientKey;
import java.util.Objects;

/**
 * A value the context carries down to a component's children: its name and type. Compared by identity, so keep each
 * in a constant.
 * <pre>{@code
 * static final ContextProperty<User> USER = ContextProperty.of("user", User.class);
 * static final ContextProperty<String> ACCENT = ContextProperty.of("accent", String.class, "blue");   // blue where none is
 *
 * app.session().provide(USER, signedIn);
 * Context.provide(sidebar, ACCENT, "red");     // the sidebar and everything in it see red
 * String accent = Context.get(button, ACCENT);
 * }</pre>
 *
 * @param <T> what it carries
 */
public final class ContextProperty<T> {

    private final String name;
    private final Class<T> type;
    private final T fallback;
    /** Where a component keeps what it provides. */
    final ClientKey<Object> key;

    private ContextProperty(String name, Class<T> type, T fallback) {
        this.name = Objects.requireNonNull(name, "name");
        this.type = Objects.requireNonNull(type, "type");
        this.fallback = fallback;
        this.key = ClientKey.of("context." + name, Object.class);
    }

    public static <T> ContextProperty<T> of(String name, Class<T> type) {
        return new ContextProperty<>(name, type, null);
    }

    /** With a value for where none was provided. */
    public static <T> ContextProperty<T> of(String name, Class<T> type, T fallback) {
        return new ContextProperty<>(name, type, fallback);
    }

    public String name() {
        return name;
    }

    public Class<T> type() {
        return type;
    }

    T fallback() {
        return fallback;
    }

    @Override
    public String toString() {
        return "ContextProperty[" + name + ": " + type.getSimpleName() + "]";
    }
}
