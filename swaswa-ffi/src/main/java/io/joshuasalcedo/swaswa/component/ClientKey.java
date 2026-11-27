package io.joshuasalcedo.swaswa.component;

import java.util.Objects;

/**
 * A key for the app's own data on a component ({@link Component#putClientProperty}), typed: what is put under it is a
 * {@code T}, and what is read back is one.
 * <pre>{@code
 * static final ClientKey<Order> ORDER = ClientKey.of("order", Order.class);
 * row.putClientProperty(ORDER, order);
 * Order o = row.getClientProperty(ORDER);
 * }</pre>
 * Keys are the same only when they are the same object: two made with one name are two keys.
 *
 * @param <T> what it holds
 */
public final class ClientKey<T> {

    private final String name;
    private final Class<T> type;

    private ClientKey(String name, Class<T> type) {
        this.name = Objects.requireNonNull(name, "name");
        this.type = Objects.requireNonNull(type, "type");
    }

    public static <T> ClientKey<T> of(String name, Class<T> type) {
        return new ClientKey<>(name, type);
    }

    public String name() {
        return name;
    }

    public Class<T> type() {
        return type;
    }

    @Override
    public String toString() {
        return "ClientKey[" + name + ": " + type.getSimpleName() + "]";
    }
}
