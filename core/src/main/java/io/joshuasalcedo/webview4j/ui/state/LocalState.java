package io.joshuasalcedo.webview4j.ui.state;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * State the app keeps itself (no server): a {@link Store} shared by every screen, a {@link Saved} one screen's.
 * A constant is the definition; its value is the current {@link Session}'s, so each person (each browser session, on
 * the web) has their own. Reading it in {@code render()} draws the screen again when it changes.
 */
public abstract sealed class LocalState<T> permits Store, Saved {

    private final String key;
    private final Codec<T> codec;
    private final Supplier<T> initial;
    private volatile Property property;

    LocalState(String key, Codec<T> codec, Supplier<T> initial, Property property) {
        this.key = Objects.requireNonNull(key, "key");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.initial = Objects.requireNonNull(initial, "initial");
        this.property = property;
    }

    public String key() {
        return key;
    }

    /** How long it lives in memory and on disk. */
    @SuppressWarnings("unchecked")
    public <S extends LocalState<T>> S property(Property property) {
        this.property = Objects.requireNonNull(property, "property");
        return (S) this;
    }

    Property resolved() {
        return Property.resolve(key, property);
    }

    /** The value now (and the screen reading it is drawn again when it changes). */
    public T get() {
        Value<T> c = cell();
        c.observeWhole();
        return c.value();
    }

    /** A part of the value: the screen is drawn again only when that part changes. */
    public <R> R select(Function<? super T, R> part) {
        Value<T> c = cell();
        R now = part.apply(c.value());
        c.observeSelected(() -> part.apply(c.value()), now);
        return now;
    }

    public void set(T value) {
        cell().set(value);
    }

    /** The value changed from what it is: {@code cart.update(c -> c.add(item))}. */
    public void update(UnaryOperator<T> change) {
        cell().update(change);
    }

    /** Back to its first value (and off the disk). */
    public void reset() {
        cell().reset();
    }

    private Value<T> cell() {
        SessionState state = SessionState.of(Session.current());
        return state.cell(storageKey(), k -> new Value<>(k, state, this));
    }

    abstract String storageKey();

    /** The value in one session. */
    static final class Value<T> extends Cell {
        private final LocalState<T> definition;
        private T value;
        private boolean loaded;

        Value(String key, SessionState session, LocalState<T> definition) {
            super(key, session);
            this.definition = definition;
        }

        @Override
        Property property() {
            return definition.resolved();
        }

        synchronized T value() {
            if (!loaded) {
                loaded = true;
                Property p = property();
                value = p.onDisk()
                        ? session.read(key).flatMap(b -> Kept.decode(b, definition.codec, p.version(), p.persistedFor().toMillis()))
                                .map(Kept::value).orElseGet(definition.initial)
                        : definition.initial.get();
            }
            return value;
        }

        void set(T next) {
            synchronized (this) {
                value();
                if (Objects.equals(value, next)) return;
                value = next;
                keep();
            }
            changed();
        }

        void update(UnaryOperator<T> change) {
            synchronized (this) {
                T next = change.apply(value());
                if (Objects.equals(value, next)) return;
                value = next;
                keep();
            }
            changed();
        }

        void reset() {
            synchronized (this) {
                value = definition.initial.get();
                loaded = true;
                if (property().onDisk()) session.delete(key);
            }
            changed();
        }

        private void keep() {
            Property p = property();
            if (p.onDisk()) session.write(key, new Kept<>(p.version(), System.currentTimeMillis(), null, value).encode(definition.codec));
        }

        /** Readers that read the whole value, and the parts the others read. */
        private final java.util.Set<Tracking.Reader> whole =
                java.util.Collections.synchronizedSet(java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>()));
        private final java.util.Map<Tracking.Reader, java.util.List<Selection<?>>> selected =
                java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

        /** A part a reader read, and what it was when the reader was last told. */
        private static final class Selection<R> {
            private final Supplier<R> part;
            private R was;

            Selection(Supplier<R> part, R was) {
                this.part = part;
                this.was = was;
            }

            /** Whether the part changed since; if it did, it is now what the reader is told of. */
            synchronized boolean differs() {
                R now = part.get();
                if (Objects.equals(now, was)) return false;
                was = now;
                return true;
            }
        }

        void observeWhole() {
            observe();
            Tracking.Reader r = Tracking.current();
            if (r != null) whole.add(r);
        }

        /** Reads a part: the reader is told of a change only when that part changes. */
        <R> void observeSelected(Supplier<R> part, R now) {
            observe();
            Tracking.Reader r = Tracking.current();
            if (r != null) selected.computeIfAbsent(r, x -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(new Selection<>(part, now));
        }

        @Override
        void evict() {
            synchronized (this) {
                if (!loaded) return;
                // Kept on disk: read back the next time a screen shows it. Not on disk: a store keeps its value.
                if (property().onDisk()) {
                    loaded = false;
                    value = null;
                }
            }
        }

        /** Only readers that read the whole value, or whose part changed, are told. */
        @Override
        boolean wants(Tracking.Reader reader) {
            if (whole.contains(reader)) return true;
            java.util.List<Selection<?>> parts = selected.get(reader);
            if (parts == null) return true;
            boolean any = false;
            for (Selection<?> s : parts) any |= s.differs();   // every part brought up to date, not only the first
            return any;
        }
    }
}
