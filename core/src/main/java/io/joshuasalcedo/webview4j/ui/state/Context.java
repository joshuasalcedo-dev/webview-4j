package io.joshuasalcedo.webview4j.ui.state;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Values a screen and everything under it can read without passing them down: the signed-in user, a theme's accent,
 * a service. A value is provided at some level and seen by everything below it, the nearest one winning:
 * <ol>
 *   <li>the {@link Session} (every window of one person's app; on the web, one browser session),</li>
 *   <li>a window ({@code uiWindow.provide(key, value)}),</li>
 *   <li>a part of a screen ({@link #with}).</li>
 * </ol>
 * <pre>{@code
 * static final ContextProperty<User> USER = ContextProperty.of("user");
 *
 * Session.of(app).provide(USER, signedIn);
 * Context.with(ACCENT, "red", () -> panel())   // what panel() builds, and its handlers, see red
 *
 * User me = Context.get(USER);                 // in render() or a handler
 * }</pre>
 * A screen's {@code render()} and its handlers run inside their window's context; a handler keeps the context it was
 * made in, so {@code get} works there too, later and on another thread.
 */
public final class Context {

    /** One level of values, over the one around it; the outermost belongs to a session. */
    private record Frame(Session session, Frame parent, Map<ContextProperty<?>, Object> values) {
        Object find(ContextProperty<?> key) {
            for (Frame f = this; f != null; f = f.parent) {
                if (f.values.containsKey(key)) return f.values.get(key);
            }
            return session == null ? MISSING : session.values.getOrDefault(key, MISSING);
        }

        Frame with(ContextProperty<?> key, Object value) {
            Map<ContextProperty<?>, Object> one = new HashMap<>(1);
            one.put(key, value == null ? NULL : value);
            return new Frame(session, this, one);
        }
    }

    static final Object NULL = new Object();
    private static final Object MISSING = new Object();
    private static final ScopedValue<Frame> CURRENT = ScopedValue.newInstance();

    private Context() {}

    /** The nearest value provided for {@code key}, or its fallback; an exception when there is neither. */
    public static <T> T get(ContextProperty<T> key) {
        return find(key).orElseThrow(() -> new IllegalStateException(
                "nothing provided for " + key.name() + ": session.provide(...), window.provide(...) or Context.with(...)"));
    }

    /** The nearest value provided for {@code key}, its fallback, or empty. */
    @SuppressWarnings("unchecked")
    public static <T> Optional<T> find(ContextProperty<T> key) {
        Object v = CURRENT.isBound() ? CURRENT.get().find(key) : MISSING;
        if (v == MISSING || v == NULL) return Optional.ofNullable(key.fallback());
        return Optional.of((T) v);
    }

    /** {@code build} run with {@code key} provided: what it builds, and the handlers it makes, see {@code value}. */
    public static <T, R> R with(ContextProperty<T> key, T value, Supplier<R> build) {
        Frame frame = (CURRENT.isBound() ? CURRENT.get() : new Frame(null, null, Map.of())).with(key, value);
        return ScopedValue.where(CURRENT, frame).call(build::get);
    }

    static Optional<Session> session() {
        return CURRENT.isBound() ? Optional.ofNullable(CURRENT.get().session) : Optional.empty();
    }

    static <R> R inSession(Session session, Supplier<R> run) {
        return ScopedValue.where(CURRENT, new Frame(session, null, Map.of())).call(run::get);
    }

    // ---- for the renderer ----

    /** A window's own values: the level between its session and its screens. */
    public static final class Scope {
        private final Session session;
        private final Map<ContextProperty<?>, Object> values = new java.util.concurrent.ConcurrentHashMap<>();

        public Scope(Session session) {
            this.session = Objects.requireNonNull(session, "session");
        }

        public Session session() {
            return session;
        }

        public <T> Scope provide(ContextProperty<T> key, T value) {
            values.put(key, value == null ? NULL : value);
            return this;
        }

        /** {@code run} with this scope's values over its session's. */
        public <R> R call(Supplier<R> run) {
            return ScopedValue.where(CURRENT, new Frame(session, null, Map.copyOf(values))).call(run::get);
        }
    }

    /** {@code handler} kept with the context it was made in: for components' handlers, which run later. */
    public static Consumer<Object> bind(Consumer<Object> handler) {
        if (!CURRENT.isBound()) return handler;
        Frame frame = CURRENT.get();
        return value -> ScopedValue.where(CURRENT, frame).run(() -> handler.accept(value));
    }
}
