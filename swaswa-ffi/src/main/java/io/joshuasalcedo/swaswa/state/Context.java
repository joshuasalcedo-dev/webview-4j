package io.joshuasalcedo.swaswa.state;

import io.joshuasalcedo.swaswa.component.Component;
import java.util.Objects;
import java.util.Optional;

/**
 * Values a component and everything inside it can read without passing them down: the signed-in user, an accent, a
 * service. Provided at some level and seen by everything below it, the nearest one winning: a component
 * ({@link #provide}), one of its parents, the app's {@link Session} at the top.
 * <pre>{@code
 * static final ContextProperty<User> USER = ContextProperty.of("user", User.class);
 *
 * app.session().provide(USER, signedIn);
 * Context.provide(adminPanel, USER, impersonated);   // the panel, and what is in it, see another user
 *
 * User me = Context.get(saveButton, USER);           // from the button up: its panel's, else the session's
 * }</pre>
 * It is read where it is asked for, from the component's place now: a component moved to another parent sees that
 * parent's values.
 */
public final class Context {

    /** What was provided as null (to hide a value further up). */
    static final Object NULL = new Object();

    private Context() {}

    /** {@code component}, and everything inside it, see {@code value} for {@code key}. */
    public static <T> void provide(Component<?> component, ContextProperty<T> key, T value) {
        Objects.requireNonNull(component, "component").putClientProperty(key.key, value == null ? NULL : value);
    }

    /** {@code component} provides nothing for {@code key} any more: it sees what its parents provide. */
    public static void remove(Component<?> component, ContextProperty<?> key) {
        Objects.requireNonNull(component, "component").putClientProperty(key.key, null);
    }

    /** The nearest value provided for {@code key}, from {@code component} up, or its fallback; an exception when neither. */
    public static <T> T get(Component<?> component, ContextProperty<T> key) {
        return find(component, key).orElseThrow(() -> new IllegalStateException(
                "nothing provided for " + key.name() + ": Context.provide(component, ...) or session.provide(...)"));
    }

    /** The nearest value provided for {@code key}, from {@code component} up (to its session), its fallback, or empty. */
    public static <T> Optional<T> find(Component<?> component, ContextProperty<T> key) {
        Objects.requireNonNull(key, "key");
        for (Component<?> c = component; c != null; c = c.getParent()) {
            Object v = c.getClientProperty(key.key);
            if (v == null && c instanceof SessionOwner owner) v = owner.session().provided(key).orElse(null);
            if (v != null) return v == NULL ? Optional.ofNullable(key.fallback()) : Optional.of(key.type().cast(v));
        }
        return Optional.ofNullable(key.fallback());
    }

    /** The session {@code component} is in: its root's ({@link SessionOwner}); empty while it is in no page. */
    public static Optional<Session> session(Component<?> component) {
        for (Component<?> c = component; c != null; c = c.getParent()) {
            if (c instanceof SessionOwner owner) return Optional.of(owner.session());
        }
        return Optional.empty();
    }
}
