package io.joshuasalcedo.swaswa.state;

import io.joshuasalcedo.swaswa.component.Property;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * State the app keeps itself (no server): a {@link Store} shared by every part of the app, a {@link Saved} one
 * screen's. A constant is the definition; its value is a {@link Session}'s, so each person (each browser session, on
 * the web) has their own. In a session it is a live {@link Property}: bind a component to it and the component
 * follows it; set it (or {@link #set}) and every component bound to it changes.
 * <pre>{@code
 * label.textProperty().bind(COUNT.in(session).map(String::valueOf));
 * COUNT.update(session, n -> n + 1);
 * }</pre>
 *
 * @param <T> what it holds
 */
public abstract sealed class LocalState<T> permits Store, Saved {

    private final String key;
    private final Codec<T> codec;
    private final Supplier<T> initial;
    private volatile Lifetime lifetime;

    LocalState(String key, Codec<T> codec, Supplier<T> initial, Lifetime lifetime) {
        this.key = Objects.requireNonNull(key, "key");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.initial = Objects.requireNonNull(initial, "initial");
        this.lifetime = lifetime;
    }

    public String key() {
        return key;
    }

    /** How long it lives in memory and on disk. */
    @SuppressWarnings("unchecked")
    public <S extends LocalState<T>> S lifetime(Lifetime lifetime) {
        this.lifetime = Objects.requireNonNull(lifetime, "lifetime");
        return (S) this;
    }

    /** Its value in {@code session}, live: setting it sets the state; components bound to it follow it. */
    public Property<T> in(Session session) {
        return cell(session).property;
    }

    /** A part of its value in {@code session}, live: told only when that part changes. */
    public <R> Property<R> select(Session session, Function<? super T, ? extends R> part) {
        return in(session).map(part);
    }

    public T get(Session session) {
        return in(session).get();
    }

    public void set(Session session, T value) {
        in(session).set(value);
    }

    /** The value changed from what it is: {@code CART.update(session, c -> c.add(item))}. */
    public void update(Session session, UnaryOperator<T> change) {
        Value<T> c = cell(session);
        synchronized (c) {
            c.property.set(change.apply(c.property.get()));
        }
    }

    /** Back to its first value (and off the disk). */
    public void reset(Session session) {
        Value<T> c = cell(session);
        synchronized (c) {
            c.resetting = true;
            try {
                c.property.set(initial.get());
            } finally {
                c.resetting = false;
            }
            if (c.lifetime().onDisk()) session.delete(c.key);
        }
    }

    private Value<T> cell(Session session) {
        Objects.requireNonNull(session, "session");
        return session.cell(storageKey(), k -> new Value<>(k, session, this));
    }

    abstract String storageKey();

    /** The value in one session: read from disk the first time, written back (soon, in the background) as it changes. */
    static final class Value<T> extends Cell {
        private final LocalState<T> definition;
        final Property<T> property;
        volatile boolean resetting;

        Value(String key, Session session, LocalState<T> definition) {
            super(key, session);
            this.definition = definition;
            Lifetime l = lifetime();
            T start = l.onDisk()
                    ? session.read(key).flatMap(b -> Kept.decode(b, definition.codec, l.version(), l.persistedFor().toMillis()))
                            .map(Kept::value).orElseGet(definition.initial)
                    : definition.initial.get();
            this.property = new Property<>(start);
            property.addListener((old, now) -> keep(now));
        }

        @Override
        Lifetime lifetime() {
            return session.resolve(definition.key(), definition.lifetime);   // patterns match its own key, not where it is kept
        }

        private void keep(T value) {
            Lifetime l = lifetime();
            if (l.onDisk() && !resetting) session.write(key, new Kept<>(l.version(), System.currentTimeMillis(), null, value).encode(definition.codec));
        }

        /** Its own listener (writing it to disk) is always there: a local value is never let go of. */
        @Override
        boolean shown() {
            return true;
        }
    }
}
