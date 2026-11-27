package io.joshuasalcedo.swaswa.state;

import java.util.function.Supplier;

/**
 * The app's own state, shared by every part of it in a session: the cart, the chosen workspace, what the person
 * prefers. In memory unless its {@link Lifetime} keeps it on disk.
 * <pre>{@code
 * record Cart(List<Item> items) {}
 * static final Store<Cart> CART = Store.of("cart", Cart.class, () -> new Cart(List.of()))
 *         .lifetime(Lifetime.of().persist());
 *
 * CART.update(session, c -> new Cart(append(c.items(), item)));
 * badge.textProperty().bind(CART.select(session, c -> String.valueOf(c.items().size())));
 * }</pre>
 *
 * @param <T> what it holds
 */
public final class Store<T> extends LocalState<T> {

    private Store(String key, Codec<T> codec, Supplier<T> initial) {
        super(key, codec, initial, null);
    }

    /** A store of a record (or anything a {@link Codec} keeps), starting at {@code initial}. */
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
