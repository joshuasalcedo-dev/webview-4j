package io.joshuasalcedo.swaswa.state;

import io.joshuasalcedo.swaswa.ffi.NativeStore;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * One person's use of the app: where their {@link Store}s, {@link Saved} values and {@link ServerState} live, the top
 * of the {@link Context}, and how long values live ({@link Lifetime}). A desktop app has one
 * ({@code SwaApplication.session()}); on the web, each browser session has its own, so nothing one person has is seen
 * by another. Nothing of the state is kept per process: it is all a session's.
 * <pre>{@code
 * Session session = app.session();
 * session.provide(USER, signedIn);                          // every component of the app sees USER
 * session.lifetime("reference.>", Lifetime.of().persist());  // for every key under reference.
 * }</pre>
 */
public final class Session implements AutoCloseable {

    private static final System.Logger LOG = System.getLogger(Session.class.getName());
    private static final byte[] DELETE = new byte[0];
    private static final Duration WRITE_DELAY = Duration.ofMillis(50);

    private final Map<ContextProperty<?>, Object> values = new ConcurrentHashMap<>();
    private final Map<String, Cell> cells = new ConcurrentHashMap<>();
    private final Map<String, byte[]> pending = new LinkedHashMap<>();
    private final List<Consumer<String>> invalidations = new CopyOnWriteArrayList<>();
    private final List<Map.Entry<String, Lifetime>> patterns = new CopyOnWriteArrayList<>();
    private volatile Lifetime defaults = Lifetime.DEFAULT;
    private volatile Supplier<StateStorage> storageMaker;
    private volatile StateStorage storage;
    private volatile boolean writerRunning;
    private volatile Thread sweeper;
    private volatile boolean closed;

    /** A session keeping what is persisted in memory, unless given a storage ({@link #storage}). */
    public Session() {}

    /** A session keeping what is persisted in the app's own store ({@link NativeStore#forApp}), opened when first needed. */
    public static Session forApp(String app) {
        Objects.requireNonNull(app, "app");
        Session s = new Session();
        s.storageMaker = () -> StateStorage.of(NativeStore.forApp(app));
        return s;
    }

    // ---- the context's top ----

    /** A value every component under this session sees, unless one nearer provides another ({@link Context}). */
    public <T> Session provide(ContextProperty<T> key, T value) {
        values.put(Objects.requireNonNull(key, "key"), value == null ? Context.NULL : value);
        return this;
    }

    /** What this session provides for {@code key}: empty when it provides nothing (not even null). */
    @SuppressWarnings("unchecked")
    <T> Optional<Object> provided(ContextProperty<T> key) {
        return Optional.ofNullable(values.get(key));
    }

    // ---- lifetimes ----

    /** The lifetime of every value without one of its own and with no matching pattern. */
    public Session lifetime(Lifetime defaults) {
        this.defaults = Objects.requireNonNull(defaults, "defaults");
        return this;
    }

    /**
     * The lifetime of every key {@code pattern} matches, as subjects match: {@code "orders"}, {@code "orders.*"} (one
     * more part), {@code "reference.>"} (one or more). The first pattern that matches wins.
     */
    public Session lifetime(String pattern, Lifetime lifetime) {
        patterns.add(Map.entry(Objects.requireNonNull(pattern, "pattern"), Objects.requireNonNull(lifetime, "lifetime")));
        return this;
    }

    /** The lifetime of a key: its own, then the first pattern that matches, then the defaults. */
    Lifetime resolve(String key, Lifetime own) {
        if (own != null) return own;
        for (Map.Entry<String, Lifetime> p : patterns) if (Keys.matches(p.getKey(), key)) return p.getValue();
        return defaults;
    }

    // ---- invalidations ----

    /** Marks every server state of the session whose key matches {@code pattern} changed, and tells its listeners. */
    public void invalidate(String pattern) {
        invalidateHere(pattern);
        for (Consumer<String> l : invalidations) {
            try {
                l.accept(pattern);
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "an invalidation listener failed", e);
            }
        }
    }

    /** Only this session's server state, not its listeners: for an invalidation that came from outside. */
    void invalidateHere(String pattern) {
        for (Cell c : cells.values()) if (c instanceof ServerState.Cell<?> s && Keys.matches(pattern, s.definition.key())) s.invalidate();
    }

    /** The network came back: what says so ({@code refetchOnReconnect}) is checked again. */
    void reconnected() {
        for (Cell c : cells.values()) if (c instanceof ServerState.Cell<?> s && s.lifetime().refetchesOnReconnect()) s.invalidate();
    }

    /**
     * Told of every invalidation in the session (a pattern of keys): to pass it on, to the pages (a React page's
     * queries) or to another process. Returns what stops it.
     */
    public Runnable onInvalidate(Consumer<String> listener) {
        invalidations.add(Objects.requireNonNull(listener, "listener"));
        return () -> invalidations.remove(listener);
    }

    // ---- cells ----

    @SuppressWarnings("unchecked")
    <C extends Cell> C cell(String key, Function<String, C> make) {
        if (closed) throw new IllegalStateException("the session is closed");
        startSweeper();
        C c = (C) cells.computeIfAbsent(key, make);
        c.used();
        return c;
    }

    // ---- disk ----

    /** Where the session keeps what is persisted: the desktop's store, the browser's, memory in tests. */
    public Session storage(StateStorage storage) {
        this.storage = Objects.requireNonNull(storage, "storage");
        return this;
    }

    private StateStorage storage() {
        StateStorage s = storage;
        if (s == null) {
            synchronized (this) {
                if (storage == null) storage = storageMaker != null ? storageMaker.get() : StateStorage.memory();
                s = storage;
            }
        }
        return s;
    }

    Optional<byte[]> read(String key) {
        synchronized (pending) {
            byte[] p = pending.get(key);
            if (p != null) return p == DELETE ? Optional.empty() : Optional.of(p);
        }
        try {
            return storage().get(key);
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "the state " + key + " could not be read from disk", e);
            return Optional.empty();
        }
    }

    /** Written soon, in the background: the latest value per key wins. */
    void write(String key, byte[] bytes) {
        synchronized (pending) {
            pending.put(key, bytes);
            if (writerRunning) return;
            writerRunning = true;
        }
        Thread.ofVirtual().name("swaswa-state-writer").start(() -> {
            try {
                Thread.sleep(WRITE_DELAY);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            flush();
        });
    }

    void delete(String key) {
        write(key, DELETE);
    }

    /** Writes now what is waiting to be written. */
    public void flush() {
        Map<String, byte[]> batch;
        synchronized (pending) {
            batch = new LinkedHashMap<>(pending);
            pending.clear();
            writerRunning = false;
        }
        if (batch.isEmpty()) return;
        StateStorage s = storage();
        batch.forEach((key, bytes) -> {
            try {
                if (bytes == DELETE) s.delete(key);
                else s.put(key, bytes);
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "the state " + key + " could not be written to disk", e);
            }
        });
    }

    // ---- letting go ----

    private void startSweeper() {
        if (sweeper != null || closed) return;
        synchronized (this) {
            if (sweeper != null) return;
            sweeper = Thread.ofVirtual().name("swaswa-state-sweeper").start(() -> {
                while (!closed) {
                    try {
                        Thread.sleep(Duration.ofMillis(250));
                    } catch (InterruptedException e) {
                        return;
                    }
                    sweep();
                }
            });
        }
    }

    /** Lets go of what nothing has shown for its {@code keepInMemory}, and runs the cells' timers. */
    void sweep() {
        long now = System.nanoTime();
        for (Cell c : cells.values()) {
            try {
                c.tick();
                if (!c.shown() && now - c.lastUsed > c.lifetime().inMemoryFor().toNanos()) c.evict();
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "the state " + c.key + " failed on its timer", e);
            }
        }
    }

    /** Ends the session (its app stopped, its browser session closed): what waits is written, the rest let go. */
    @Override
    public void close() {
        if (closed) return;
        closed = true;
        Thread s = sweeper;
        if (s != null) s.interrupt();
        flush();
        StateStorage st = storage;
        if (st != null) st.close();
        cells.clear();
        values.clear();
    }

    public boolean isClosed() {
        return closed;
    }
}
