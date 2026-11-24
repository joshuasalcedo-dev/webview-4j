package io.joshuasalcedo.webview4j.ui.state;

import java.util.function.Supplier;

/**
 * The app's own state, shared by every screen and window of a session: the cart, the chosen workspace, what the
 * person prefers. Changed through {@link #set} and {@link #update}; a screen that reads it is drawn again when it
 * changes, and one that reads only a part ({@link #select}) only when that part does. In memory unless its
 * {@link Property} keeps it on disk.
 * <pre>{@code
 * record Cart(List<Item> items) {}
 * static final Store<Cart> CART = Store.of("cart", Cart.class, () -> new Cart(List.of()))
 *         .property(Property.of().persist());
 *
 * CART.update(c -> new Cart(append(c.items(), item)));
 * int count = CART.select(c -> c.items().size());   // in render(): drawn again when the count changes
 * }</pre>
 */
public final class Store<T> extends LocalState<T> {

    private Store(String key, Codec<T> codec, Supplier<T> initial) {
        super(key, codec, initial, null);
    }

    /** A store of a record (or anything {@link Codec} keeps), starting at {@code initial}. */
    public static <T> Store<T> of(String key, Class<T> type, Supplier<T> initial) {
        return new Store<>(key, Codec.of(type), initial);
    }

    public static <T> Store<T> of(String key, Codec<T> codec, Supplier<T> initial) {
        return new Store<>(key, codec, initial);
    }

    @Override
    String storageKey() {
        return "store." + key();
    }

    @Override
    public String toString() {
        return "Store[" + key() + "]";
    }
}
