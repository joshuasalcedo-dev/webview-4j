package io.joshuasalcedo.webview4j.ui.state;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/** One value in one session (a store's, a server state's): who reads it, and when it was last used. */
abstract class Cell {

    final String key;
    final SessionState session;
    private final Set<Tracking.Reader> readers = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    volatile long lastRead = System.nanoTime();

    Cell(String key, SessionState session) {
        this.key = key;
        this.session = session;
    }

    /** The current render reads it: true when its window starts showing it (did not read it the render before). */
    final boolean observe() {
        lastRead = System.nanoTime();
        Tracking.Reader reader = Tracking.current();
        if (reader == null) return false;
        readers.add(reader);
        return reader.read(this);
    }

    /** Whether a window shows it now. */
    final boolean shown() {
        synchronized (readers) {
            readers.removeIf(r -> !r.alive());
            return !readers.isEmpty();
        }
    }

    /** Draws again the windows that show it. */
    final void changed() {
        List<Tracking.Reader> now;
        synchronized (readers) {
            now = List.copyOf(readers);
        }
        for (Tracking.Reader r : now) if (wants(r)) r.changed();
    }

    /** Whether {@code reader} cares about this change (a store's select: only when its part changed). */
    boolean wants(Tracking.Reader reader) {
        return true;
    }

    abstract Property property();

    /** Dropped from memory (it stays on disk, if persisted): no window has shown it for {@code keepInMemory}. */
    abstract void evict();

    /** For the sweeper: polling, and anything else on a timer. */
    void tick() {}
}
