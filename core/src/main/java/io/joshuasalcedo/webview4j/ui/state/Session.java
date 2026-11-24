package io.joshuasalcedo.webview4j.ui.state;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One person's use of the app: the top of the context, and where their stores and server state live. On the desktop
 * an app has one; deployed on the web, each browser session has its own, so nothing one person has is seen by
 * another. Nothing in {@code ui.state} is kept per process: it is all a session's.
 * <pre>{@code
 * Session session = Session.of(app);       // the desktop app's one session
 * session.provide(USER, signedIn);         // every window and screen of it sees USER
 * }</pre>
 */
public final class Session {

    /** The desktop sessions, by what they belong to (an App, or a web session's id). */
    private static final Map<Object, Session> SESSIONS = new ConcurrentHashMap<>();

    private final Object owner;
    final Map<ContextProperty<?>, Object> values = new ConcurrentHashMap<>();
    final Map<Class<?>, Object> parts = new ConcurrentHashMap<>();
    private final java.util.List<java.util.function.Consumer<String>> invalidations = new java.util.concurrent.CopyOnWriteArrayList<>();

    private Session(Object owner) {
        this.owner = owner;
    }

    /** The session of {@code owner} (an App on the desktop, a web session), made the first time. */
    public static Session of(Object owner) {
        return SESSIONS.computeIfAbsent(Objects.requireNonNull(owner, "owner"), Session::new);
    }

    /** The session the current screen or handler runs in; an exception outside one. */
    public static Session current() {
        return Context.session().orElseThrow(() -> new IllegalStateException(
                "no session here: this runs in a screen's render() or a handler, or in session.call(...)"));
    }

    /** Ends the session (its app stopped, its browser session closed): its values and state are let go. */
    public void close() {
        SESSIONS.remove(owner, this);
        Object state = parts.get(SessionState.class);
        if (state instanceof SessionState s) s.close();
        values.clear();
        parts.clear();
    }

    /** A value every window and screen of the session sees (the top of the context). */
    public <T> Session provide(ContextProperty<T> key, T value) {
        values.put(key, value == null ? Context.NULL : value);
        return this;
    }

    /** Where the session keeps what is persisted: the desktop's {@code NativeStore}, the browser's, memory in tests. */
    public Session storage(StateStorage storage) {
        SessionState.of(this).storageNow(java.util.Objects.requireNonNull(storage, "storage"));
        return this;
    }

    /** The storage to open the first time it is needed, unless one was given. */
    public Session defaultStorage(java.util.function.Supplier<StateStorage> storage) {
        SessionState.of(this).defaultStorage(storage);
        return this;
    }

    /** Writes now what is waiting to be written. */
    public void flush() {
        SessionState.of(this).flush();
    }

    /**
     * Told of every invalidation in the session (a pattern of keys): to pass it on, to the pages (a React page's
     * TanStack queries) or to another process. Returns what stops it.
     */
    public Runnable onInvalidate(java.util.function.Consumer<String> listener) {
        invalidations.add(listener);
        return () -> invalidations.remove(listener);
    }

    void invalidated(String pattern) {
        for (var l : invalidations) {
            try {
                l.accept(pattern);
            } catch (RuntimeException e) {
                System.getLogger(Session.class.getName()).log(System.Logger.Level.WARNING, "an invalidation listener failed", e);
            }
        }
    }

    /** {@code run} inside this session: what it reads of the context and of the stores is the session's. */
    public <R> R call(java.util.function.Supplier<R> run) {
        return Context.inSession(this, run);
    }

    /** A part of the session made once (its stores, its server state cache): for {@code ui.state}'s own classes. */
    @SuppressWarnings("unchecked")
    <T> T part(Class<T> type, java.util.function.Supplier<T> make) {
        return (T) parts.computeIfAbsent(type, t -> make.get());
    }

    @Override
    public String toString() {
        return "Session[" + owner + "]";
    }
}
