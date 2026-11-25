package io.joshuasalcedo.webview4j.ui.state;

import java.util.Collections;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Who reads the state: while a window renders, every {@link Store}, {@link Saved} and {@link ServerState} its screen
 * reads remembers the window, and a change draws that window again (and only it). For the renderer; screens never
 * use it themselves.
 */
public final class Tracking {

    private static final ScopedValue<Reader> CURRENT = ScopedValue.newInstance();

    private Tracking() {}

    /**
     * One render of one window. A new one per render: what the render before read is {@code previous}, so a value
     * the window did not show before is new to it (and fetched if stale).
     */
    public static final class Reader {
        private final Runnable onChange;
        private final Set<Object> reads = Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
        private final Set<Object> previous;
        private volatile boolean alive = true;

        /** {@code onChange}: what to do when something this render read changes (draw the window again). */
        public Reader(Runnable onChange, Reader before) {
            this.onChange = onChange;
            this.previous = before == null ? Set.of() : before.reads;
            if (before != null) before.alive = false;
        }

        /** No longer read: a newer render replaced it, or its window closed. */
        public void end() {
            alive = false;
        }

        public boolean alive() {
            return alive;
        }

        /** What this render read, for the window (refetch on focus). */
        Set<Object> reads() {
            return reads;
        }

        /**
         * Remembers {@code cell}; true when the render before did not read it (the window starts showing it), and only
         * for its first read in this render: a render reads a value several times (its status, its data), and one
         * start is one fetch.
         */
        boolean read(Object cell) {
            return reads.add(cell) && !previous.contains(cell);
        }

        void changed() {
            if (alive) onChange.run();
        }
    }

    /**
     * Something windows draw that can change: a component kept between renders. The windows that drew it are drawn
     * again when it {@link #changed() changes}. A window that drew it no longer (a newer render did not) is let go.
     */
    public static final class Watched {
        private java.util.Set<Reader> readers;

        /** The current render draws it (no-op outside one). */
        public void read() {
            Reader reader = current();
            if (reader == null) return;
            synchronized (this) {
                if (readers == null) readers = Collections.newSetFromMap(new java.util.WeakHashMap<>());
                readers.add(reader);
            }
        }

        /** Draws again the windows that show it now. */
        public void changed() {
            java.util.List<Reader> now;
            synchronized (this) {
                if (readers == null || readers.isEmpty()) return;
                readers.removeIf(r -> !r.alive());
                now = java.util.List.copyOf(readers);
            }
            for (Reader r : now) r.changed();
        }
    }

    /** {@code render} run with {@code reader} remembering what it reads. */
    public static <R> R reading(Reader reader, Supplier<R> render) {
        return ScopedValue.where(CURRENT, reader).call(render::get);
    }

    static Reader current() {
        return CURRENT.isBound() ? CURRENT.get() : null;
    }

    /** What {@code reader} read, as cells of server state: for checking them again when its window comes back to the front. */
    public static void focused(Reader reader) {
        if (reader == null) return;
        for (Object cell : reader.reads()) if (cell instanceof ServerState.Cell<?> c) c.focused();
    }
}
