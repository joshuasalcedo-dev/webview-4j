package io.joshuasalcedo.webview4j.ui.state;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A session's state: its cells by key, where they are kept on disk, writes going out in the background (the latest
 * per key, a few at a time), and a sweeper that lets go of what no window has shown for a while.
 */
final class SessionState {

    private static final System.Logger LOG = System.getLogger(SessionState.class.getName());
    private static final byte[] DELETE = new byte[0];
    private static final Duration WRITE_DELAY = Duration.ofMillis(50);

    final Session session;
    private final Map<String, Cell> cells = new ConcurrentHashMap<>();
    private final Map<String, byte[]> pending = new LinkedHashMap<>();
    private volatile Supplier<StateStorage> storageMaker;
    private volatile StateStorage storage;
    private volatile boolean writerRunning;
    private volatile Thread sweeper;
    private volatile boolean closed;

    SessionState(Session session) {
        this.session = session;
    }

    static SessionState of(Session session) {
        return session.part(SessionState.class, () -> new SessionState(session));
    }

    @SuppressWarnings("unchecked")
    <C extends Cell> C cell(String key, Function<String, C> make) {
        startSweeper();
        return (C) cells.computeIfAbsent(key, make);
    }

    Iterable<Cell> cells() {
        return cells.values();
    }

    // ---- disk ----

    void storage(Supplier<StateStorage> maker) {
        this.storageMaker = maker;
    }

    void storageNow(StateStorage s) {
        this.storage = s;
    }

    void defaultStorage(Supplier<StateStorage> maker) {
        if (storage == null && storageMaker == null) storageMaker = maker;
    }

    /** The storage, opened the first time something is read or written. */
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
        }
        startWriter();
    }

    void delete(String key) {
        write(key, DELETE);
    }

    private void startWriter() {
        synchronized (pending) {
            if (writerRunning) return;
            writerRunning = true;
        }
        Thread.ofVirtual().name("webview4j-state-writer").start(() -> {
            try {
                Thread.sleep(WRITE_DELAY);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            flush();
        });
    }

    /** Writes what is waiting, now. */
    void flush() {
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
            sweeper = Thread.ofVirtual().name("webview4j-state-sweeper").start(() -> {
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

    /** Lets go of what no window has shown for its {@code keepInMemory}, and runs the cells' timers. */
    void sweep() {
        long now = System.nanoTime();
        for (Cell c : cells.values()) {
            try {
                c.tick();
                if (!c.shown() && now - c.lastRead > c.property().inMemoryFor().toNanos()) c.evict();
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "the state " + c.key + " failed on its timer", e);
            }
        }
    }

    void close() {
        closed = true;
        Thread s = sweeper;
        if (s != null) s.interrupt();
        flush();
        StateStorage st = storage;
        if (st != null) st.close();
        cells.clear();
    }
}
