package io.joshuasalcedo.swaswa.state;

import io.joshuasalcedo.swaswa.component.Property;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.UnaryOperator;

/**
 * Data from a server, as TanStack Query keeps it: fetched in the background, cached by key in the session, shown at
 * once from memory or disk, and fetched again only when something says it changed: a {@link Mutation} or an
 * {@link #invalidate}, the server's version tag (an ETag: a 304 costs nothing), or its {@link Lifetime} saying it is
 * stale. In a session it is live properties: what it holds, its status, whether it is fetching, its error; bind
 * components to them.
 * <pre>{@code
 * static final ServerState<List<Order>> ORDERS = ServerState.of("orders", Codec.listOf(Order.class), api::orders)
 *         .lifetime(Lifetime.of().fresh(Duration.ofSeconds(30)).persist());
 *
 * table.itemsProperty().bind(ORDERS.data(session));
 * spinner.visibleProperty().bind(ORDERS.fetching(session));
 * }</pre>
 * For data that depends on something (a page, a filter), make one per value: a cheap definition, cached by its key:
 * {@code ServerState.of(ServerState.key("orders", query), codec, () -> api.orders(query))}.
 *
 * @param <T> what it holds
 */
public final class ServerState<T> {

    /** Where it is. */
    public enum Status {
        /** Nothing yet: the first fetch is under way. */
        LOADING,
        /** There is data (perhaps being fetched again: {@link #fetching}). */
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
    private volatile Lifetime lifetime;

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

    public ServerState<T> lifetime(Lifetime lifetime) {
        this.lifetime = Objects.requireNonNull(lifetime, "lifetime");
        return this;
    }

    // ---- in a session: live, read-only properties ----

    /**
     * What it holds in {@code session}: null until the first fetch arrives (or what was kept on disk, at once). Asking
     * for it is showing it (TanStack's mount): it is fetched if there is nothing yet or what there is is stale.
     */
    public Property<T> data(Session session) {
        return use(session).data;
    }

    /** Where it is. Only watches: it fetches nothing ({@link #data} does). */
    public Property<Status> status(Session session) {
        return cell(session).status;
    }

    /** Fetching now (the first time, or again in the background while the old data shows). Only watches. */
    public Property<Boolean> fetching(Session session) {
        return cell(session).fetching;
    }

    /** What went wrong the last time; null when the last fetch worked. Only watches. */
    public Property<Throwable> error(Session session) {
        return cell(session).error;
    }

    // ---- changing ----

    /** Fetches again now, whatever its lifetime says. */
    public void refetch(Session session) {
        cell(session).fetch();
    }

    /** Marks it changed: fetched again now if something shows it, otherwise the next time something asks for it. */
    public void invalidate(Session session) {
        cell(session).invalidate();
    }

    /** Sets the data here, as if fetched (after a change the app made itself; an optimistic update). */
    public void set(Session session, T value) {
        cell(session).put(value, null, true);
    }

    public void update(Session session, UnaryOperator<T> change) {
        Cell<T> c = cell(session);
        T now = c.data.get();
        if (now != null) c.put(change.apply(now), null, true);
    }

    private Cell<T> use(Session session) {
        Cell<T> c = cell(session);
        c.checkStale();
        return c;
    }

    Cell<T> cell(Session session) {
        Objects.requireNonNull(session, "session");
        return session.cell("server." + key, k -> new Cell<>(k, session, this));
    }

    @Override
    public String toString() {
        return "ServerState[" + key + "]";
    }

    /** The data in one session, and what is being done to get it. */
    static final class Cell<T> extends io.joshuasalcedo.swaswa.state.Cell {

        final ServerState<T> definition;
        // what is written (here only), and what is handed out (read-only: they follow these)
        private final Property<T> dataNow = new Property<>();
        private final Property<Status> statusNow = new Property<>(Status.LOADING);
        private final Property<Boolean> fetchingNow = new Property<>(false);
        private final Property<Throwable> errorNow = new Property<>();
        final Property<T> data = dataNow.map(v -> v);
        final Property<Status> status = statusNow.map(v -> v);
        final Property<Boolean> fetching = fetchingNow.map(v -> v);
        final Property<Throwable> error = errorNow.map(v -> v);

        private boolean loaded;
        private String etag;
        private long fetchedAt;
        private boolean stale;
        private long lastPoll = System.nanoTime();

        Cell(String key, Session session, ServerState<T> definition) {
            super(key, session);
            this.definition = definition;
        }

        @Override
        Lifetime lifetime() {
            return session.resolve(definition.key(), definition.lifetime);
        }

        @Override
        boolean shown() {
            return listened(data, status, fetching, error);
        }

        /** Asked for: what was on disk the first time, and a fetch when there is nothing yet or it is stale. */
        void checkStale() {
            boolean needs;
            synchronized (this) {
                load();
                needs = !fetchingNow.get() && (dataNow.get() == null ? errorNow.get() == null || stale : isStale());
            }
            if (needs) fetch();
        }

        private void load() {
            if (loaded) return;
            loaded = true;
            Lifetime l = lifetime();
            if (!l.onDisk()) return;
            session.read(key).flatMap(b -> Kept.decode(b, definition.codec, l.version(), l.persistedFor().toMillis())).ifPresent(k -> {
                etag = k.etag();
                fetchedAt = k.at();
                dataNow.set(k.value());
                statusNow.set(Status.SUCCESS);
            });
        }

        private boolean isStale() {
            return stale || System.currentTimeMillis() - fetchedAt >= lifetime().freshFor().toMillis();
        }

        void invalidate() {
            synchronized (this) {
                stale = true;
            }
            if (shown()) fetch();
        }

        /** Starts a fetch in the background, unless one runs. */
        void fetch() {
            synchronized (this) {
                load();
                if (fetchingNow.get()) return;
                fetchingNow.set(true);
                if (dataNow.get() == null) statusNow.set(Status.LOADING);
            }
            Thread.ofVirtual().name("swaswa-fetch-" + key).start(this::run);
        }

        private void run() {
            Lifetime l = lifetime();
            Duration wait = l.retryDelay();
            Throwable last = null;
            for (int attempt = 0; attempt <= l.retries(); attempt++) {
                try {
                    String tag;
                    synchronized (this) {
                        tag = dataNow.get() != null ? etag : null;
                    }
                    Answer<T> answer = definition.fetcher.fetch(tag);
                    if (answer.unchanged()) {
                        synchronized (this) {
                            fetchedAt = System.currentTimeMillis();
                            stale = false;
                            errorNow.set(null);
                            fetchingNow.set(false);
                            keep();
                        }
                    } else {
                        put(answer.value(), answer.etag(), false);
                    }
                    return;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    last = e;
                    break;
                } catch (Exception e) {
                    last = e;
                    if (attempt < l.retries()) {
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
                errorNow.set(last);
                if (dataNow.get() == null) statusNow.set(Status.ERROR);
                fetchingNow.set(false);
            }
        }

        void put(T next, String tag, boolean local) {
            synchronized (this) {
                loaded = true;
                if (!local) {
                    etag = tag;
                    fetchedAt = System.currentTimeMillis();
                    stale = false;
                    errorNow.set(null);
                }
                if (!local) fetchingNow.set(false);   // first: what sees the data sees the fetch done
                dataNow.set(next);
                statusNow.set(next != null ? Status.SUCCESS : statusNow.get());
                keep();
            }
        }

        private void keep() {
            Lifetime l = lifetime();
            T value = dataNow.get();
            if (l.onDisk() && value != null) session.write(key, new Kept<>(l.version(), fetchedAt, etag, value).encode(definition.codec));
        }

        /** Nothing has shown it for a while: its data is let go (still on disk, if persisted). */
        @Override
        void evict() {
            synchronized (this) {
                if (!loaded || fetchingNow.get() || dataNow.get() == null) return;
                loaded = false;
                dataNow.set(null);
                errorNow.set(null);
                statusNow.set(Status.LOADING);
            }
        }

        /** Polling: fetched again every {@code refetchEvery} while something shows it. */
        @Override
        void tick() {
            Duration every = lifetime().refetchInterval();
            if (every.isZero() || every.isNegative() || !shown()) return;
            if (System.nanoTime() - lastPoll >= every.toNanos()) {
                lastPoll = System.nanoTime();
                fetch();
            }
        }
    }
}
