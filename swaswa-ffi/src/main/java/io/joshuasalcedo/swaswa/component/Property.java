package io.joshuasalcedo.swaswa.component;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A value that tells when it changes, and can follow another: JavaFX's properties.
 * <pre>{@code
 * save.enabledProperty().bind(form.validProperty());       // save follows: enabled while the form is valid
 * name.valueProperty().addListener((old, now) -> title.setText("Hello " + now));
 * a.valueProperty().bindBidirectional(b.valueProperty());   // each follows the other
 * }</pre>
 *
 * @param <T> what it holds
 */
public final class Property<T> {

    /** Told of a change: what it held, and what it holds now. */
    @FunctionalInterface
    public interface Listener<T> {
        void changed(T old, T now);
    }

    private volatile T value;
    private final List<Listener<? super T>> listeners = new CopyOnWriteArrayList<>();
    private Property<T> following;
    private Listener<T> follower;
    private boolean changing;

    public Property() {}

    public Property(T value) {
        this.value = value;
    }

    public T get() {
        return value;
    }

    /**
     * Holds {@code value} now; its listeners are told if it changed.
     *
     * @throws IllegalStateException if it follows another ({@link #bind}): set that one
     */
    public void set(T value) {
        if (following != null) throw new IllegalStateException("bound: it follows another, set that one (or unbind() first)");
        if (readOnly) throw new IllegalStateException("a part of another property (map): set that one");
        put(value);
    }

    private void put(T now) {
        T old;
        synchronized (this) {
            if (changing || Objects.equals(value, now)) return;
            old = value;
            value = now;
            changing = true;
        }
        try {
            for (Listener<? super T> l : listeners) l.changed(old, now);
        } finally {
            synchronized (this) {
                changing = false;
            }
        }
    }

    public Property<T> addListener(Listener<? super T> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
        return this;
    }

    public Property<T> removeListener(Listener<? super T> listener) {
        listeners.remove(listener);
        return this;
    }

    /**
     * A property holding {@code part} of this one's value, as it changes: its listeners are told only when that part
     * changes (JavaFX's {@code map}). It cannot be set: it follows this one.
     * <pre>{@code
     * Property<Integer> count = cart.map(c -> c.items().size());   // told when the count changes, not every cart change
     * badge.textProperty().bind(count.map(String::valueOf));
     * }</pre>
     */
    public <R> Property<R> map(java.util.function.Function<? super T, ? extends R> part) {
        Objects.requireNonNull(part, "part");
        Property<R> mapped = new Property<>(part.apply(value));
        mapped.readOnly = true;
        addListener((old, now) -> mapped.put(part.apply(now)));
        return mapped;
    }

    /** Whether it can be set: not when it follows another ({@link #bind}) or is a part of one ({@link #map}). */
    public boolean isReadOnly() {
        return readOnly || following != null;
    }

    private volatile boolean readOnly;

    /** Whether something listens to it (a listener, or a property bound to it): whether anything shows it. */
    public boolean hasListeners() {
        return !listeners.isEmpty();
    }

    /** Follows {@code other}: holds what it holds, now and as it changes, until {@link #unbind()}. */
    public synchronized void bind(Property<T> other) {
        Objects.requireNonNull(other, "other");
        if (other == this) throw new IllegalArgumentException("a property cannot follow itself");
        unbind();
        following = other;
        follower = (old, now) -> put(now);
        other.addListener(follower);
        put(other.get());
    }

    public synchronized void unbind() {
        if (following != null) {
            following.removeListener(follower);
            following = null;
            follower = null;
        }
    }

    public boolean isBound() {
        return following != null;
    }

    /** Each follows the other: a change to either is the other's too. This one takes {@code other}'s value first. */
    public void bindBidirectional(Property<T> other) {
        Objects.requireNonNull(other, "other");
        set(other.get());
        other.addListener((old, now) -> put(now));
        addListener((old, now) -> other.put(now));
    }

    @Override
    public String toString() {
        return "Property[" + value + "]";
    }
}
