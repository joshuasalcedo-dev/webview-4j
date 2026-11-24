package io.joshuasalcedo.webview4j.ui.state;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.UnaryOperator;

/**
 * Data from a server, as TanStack Query keeps it: fetched in the background, cached by key in the session, shown at
 * once from memory or disk, and fetched again only when something says it changed: a {@link Mutation} or an
 * {@link #invalidate}, the server's version tag (an ETag: a 304 costs nothing), or its {@link Property} saying it is
 * stale. A screen that reads it is drawn again when it arrives.
 * <pre>{@code
 * static final ServerState<List<Order>> ORDERS = ServerState.of("orders", Codec.listOf(Order.class), api::orders)
 *         .property(Property.of().fresh(Duration.ofSeconds(30)).persist());
 *
 * public Component<?> render() {
 *     return DataTable.of("Orders", Order.class, ORDERS.data(List.of())).state(ORDERS);   // loading, error, a bar
 * }
 * }</pre>
 * For data that depends on something (a page, a filter), make one per value: a cheap definition, cached by its key:
 * {@code ServerState.of(Keys.of("orders", query), codec, () -> api.orders(query))}.
 */
public final class ServerState<T> {

    /** Where it is. */
    public enum Status {
        /** Nothing yet: the first fetch is under way. */
        LOADING,
        /** There is data (perhaps being fetched again: {@link #fetching()}). */
        SUCCESS,
        /** The last fetch failed and there is no data. */
        ERROR
    }

    /** What a fetch got: the value and the server's version tag, or "not changed" for the tag it was given. */
    public record Answer<T>(T value, String etag, boolean unchanged) {
        public static <T> Answer<T> of(T value) { return new Answer<>(value, null, false); }
        public static <T> Answer<T> of(T value, String etag) { return new Answer<>(value, etag, false); }
        /** The server said 304: what is kept is still right. */
        public static <T> Answer<T> notModified() { return new Answer<>(null, null, true); }
    }

    /** A fetch that can ask "changed since this version?" (an ETag): it gets the tag kept, null the first time. */
    @FunctionalInterface
    public interface Fetcher<T> {
        Answer<T> fetch(String etag) throws Exception;
    }

    private final String key;
    private final Codec<T> codec;
    private final Fetcher<T> fetcher;
    private volatile Property property;

    private ServerState(String key, Codec<T> codec, Fetcher<T> fetcher) {
        this.key = Objects.requireNonNull(key, "key");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.fetcher = Objects.requireNonNull(fetcher, "fetcher");
    }

    public static <T> ServerState<T> of(String key, Codec<T> codec, Callable<T> fetch) {
        Objects.requireNonNull(fetch, "fetch");
        return new ServerState<>(key, codec, etag -> Answer.of(fetch.call()));
    }

    public static <T> ServerState<T> of(String key, Class<T> type, Callable<T> fetch) {
        return of(key, Codec.of(type), fetch);
    }

    /** With the server's version tags: unchanged data costs a 304, not a download. */
    public static <T> ServerState<T> conditional(String key, Codec<T> codec, Fetcher<T> fetch) {
        return new ServerState<>(key, codec, fetch);
    }

    /** A key from parts: {@code key("orders", query)}. */
    public static String key(Object... parts) {
        return Keys.of(parts);
    }

    public String key() {
        return key;
    }

    public ServerState<T> property(Property property) {
        this.property = Objects.requireNonNull(property, "property");
        return this;
    }

    Property resolved() {
        return Property.resolve(key, property);
    }

    // ---- reading (in render) ----

    /** The data, if there is any yet: read in {@code render()}, it starts the fetch it needs. */
    public Optional<T> data() {
        return Optional.ofNullable(cell().read());
    }

    /** The data, or {@code fallback} while there is none. */
    public T data(T fallback) {
        T v = cell().read();
        return v == null ? fallback : v;
    }

    /** Where it is; read in {@code render()}, it starts the fetch it needs, as {@link #data()} does. */
    public Status status() {
        Cell<T> c = cell();
        c.read();
        return c.status();
    }

    /** Fetching now (the first time, or again in the background while the old data shows). */
    public boolean fetching() {
        Cell<T> c = cell();
        c.read();
        return c.fetching;
    }

    /** What went wrong the last time, if it did. */
    public Optional<Throwable> error() {
        Cell<T> c = cell();
        c.read();
        return Optional.ofNullable(c.error);
    }

    public boolean isLoading() {
        return status() == Status.LOADING;
    }

    // ---- changing ----

    /** Fetches again now, whatever its property says. */
    public void refetch() {
        cell().fetch(true);
    }

    /** Marks it changed: fetched again now if a screen shows it, otherwise the next time one does. */
    public void invalidate() {
        cell().invalidate();
    }

    /** Sets the data here, as if fetched (after a change the app made itself; an optimistic update). */
    public void set(T value) {
        cell().put(value, null, true);
    }

    public void update(UnaryOperator<T> change) {
        Cell<T> c = cell();
        T now = c.read();
        if (now != null) c.put(change.apply(now), null, true);
    }

    /**
     * Marks every server state of the session whose key matches {@code pattern} changed ({@code "orders.>"}), and
     * tells the session's listeners (the app's pages: a React page's TanStack queries with a matching key refresh).
     */
    public static void invalidate(String pattern) {
        Session session = Session.current();
        invalidateHere(session, pattern);
        session.invalidated(pattern);
    }

    /** Only this session's server state, not its listeners: for an invalidation that came from outside. */
    static void invalidateHere(Session session, String pattern) {
        for (io.joshuasalcedo.webview4j.ui.state.Cell c : SessionState.of(session).cells()) {
            if (c instanceof Cell<?> s && Keys.matches(pattern, s.definition.key)) s.invalidate();
        }
    }

    /** The network came back: what says so ({@code refetchOnReconnect}) is checked again if shown, else when next shown. */
    static void reconnected(Session session) {
        for (io.joshuasalcedo.webview4j.ui.state.Cell c : SessionState.of(session).cells()) {
            if (c instanceof Cell<?> s && s.property().refetchesOnReconnect()) s.invalidate();
        }
    }

    Cell<T> cell() {
        SessionState state = SessionState.of(Session.current());
        return state.cell("server." + key, k -> new Cell<>(k, state, this));
    }

    @Override
    public String toString() {
        return "ServerState[" + key + "]";
    }

    /** The data in one session, and what is being done to get it. */
    static final class Cell<T> extends io.joshuasalcedo.webview4j.ui.state.Cell {

        private final ServerState<T> definition;
        private T value;
        private boolean loaded;
        private String etag;
        private long fetchedAt;
        private boolean stale;
        volatile boolean fetching;
        volatile Throwable error;
        private long lastPoll = System.nanoTime();

        Cell(String key, SessionState session, ServerState<T> definition) {
            super(key, session);
            this.definition = definition;
        }

        @Override
        Property property() {
            return definition.resolved();
        }

        /** The data for a render: from memory or disk, and a fetch started if the window starts showing it stale. */
        T read() {
            boolean starts = observe();
            T v;
            boolean needs;
            synchronized (this) {
                load();
                v = value;
                needs = value == null ? !fetching && (error == null || starts) : (starts || stale) && isStale();
            }
            if (needs) fetch(false);
            return v;
        }

        synchronized Status status() {
            load();
            if (value != null) return Status.SUCCESS;
            return error != null && !fetching ? Status.ERROR : Status.LOADING;
        }

        /** What was on disk, the first time (or after it was let go from memory). */
        private void load() {
            if (loaded) return;
            loaded = true;
            Property p = property();
            if (!p.onDisk()) return;
            session.read(key).flatMap(b -> Kept.decode(b, definition.codec, p.version(), p.persistedFor().toMillis())).ifPresent(k -> {
                value = k.value();
                etag = k.etag();
                fetchedAt = k.at();
            });
        }

        private boolean isStale() {
            return stale || System.currentTimeMillis() - fetchedAt >= property().freshFor().toMillis();
        }

        void invalidate() {
            synchronized (this) {
                stale = true;
            }
            if (shown()) fetch(true);
        }

        /** Back in front: checked again if stale and its property says so. */
        void focused() {
            boolean go;
            synchronized (this) {
                go = property().refetchesOnFocus() && value != null && isStale();
            }
            if (go) fetch(false);
        }

        /** Starts a fetch in the background, unless one runs (or {@code force}: one is asked for anyway). */
        void fetch(boolean force) {
            synchronized (this) {
                if (fetching) return;
                fetching = true;
            }
            changed();
            Session owner = session.session;
            Thread.ofVirtual().name("webview4j-fetch-" + key).start(() -> owner.call(() -> {
                run();
                return null;
            }));
        }

        private void run() {
            Property p = property();
            Duration wait = p.retryDelay();
            Throwable last = null;
            for (int attempt = 0; attempt <= p.retries(); attempt++) {
                try {
                    String tag;
                    synchronized (this) {
                        tag = value != null ? etag : null;
                    }
                    Answer<T> answer = definition.fetcher.fetch(tag);
                    if (answer.unchanged()) {
                        synchronized (this) {
                            fetchedAt = System.currentTimeMillis();
                            stale = false;
                            error = null;
                            fetching = false;
                            keep();
                        }
                    } else {
                        put(answer.value(), answer.etag(), false);
                    }
                    changed();
                    return;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    last = e;
                    break;
                } catch (Exception e) {
                    last = e;
                    if (attempt < p.retries()) {
                        try {
                            Thread.sleep(wait);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                        wait = wait.multipliedBy(2);
                    }
                }
            }
            synchronized (this) {
                error = last;
                fetching = false;
            }
            changed();
        }

        void put(T next, String tag, boolean local) {
            synchronized (this) {
                loaded = true;
                value = next;
                if (!local) {
                    etag = tag;
                    fetchedAt = System.currentTimeMillis();
                    stale = false;
                    error = null;
                    fetching = false;
                }
                keep();
            }
            if (local) changed();
        }

        private void keep() {
            Property p = property();
            if (p.onDisk() && value != null) {
                session.write(key, new Kept<>(p.version(), fetchedAt, etag, value).encode(definition.codec));
            }
        }

        @Override
        void evict() {
            synchronized (this) {
                if (!loaded || fetching) return;
                loaded = false;
                value = null;
                error = null;
            }
        }

        /** Polling: fetched again every {@code refetchEvery} while a window shows it. */
        @Override
        void tick() {
            Duration every = property().refetchInterval();
            if (every.isZero() || every.isNegative() || !shown()) return;
            if (System.nanoTime() - lastPoll >= every.toNanos()) {
                lastPoll = System.nanoTime();
                fetch(false);
            }
        }
    }
}
