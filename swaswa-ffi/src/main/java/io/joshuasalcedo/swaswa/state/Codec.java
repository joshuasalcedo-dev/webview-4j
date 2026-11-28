package io.joshuasalcedo.swaswa.state;

import io.joshuasalcedo.swaswa.internal.Json;
import io.joshuasalcedo.swaswa.internal.Typed;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * How a value is kept on disk: as JSON, read back as its type. Records, lists, maps, strings, numbers, booleans and
 * enums, nested as they like.
 * <pre>{@code
 * Codec<Cart> cart = Codec.of(Cart.class);
 * Codec<List<Order>> orders = Codec.listOf(Order.class);
 * }</pre>
 *
 * @param <T> what it keeps
 */
public final class Codec<T> {

    private final Type type;
    private final String name;

    private Codec(Type type, String name) {
        this.type = Objects.requireNonNull(type, "type");
        this.name = name;
    }

    public static <T> Codec<T> of(Class<T> type) {
        return new Codec<>(type, type.getSimpleName());
    }

    public static <T> Codec<List<T>> listOf(Class<T> element) {
        return new Codec<>(parameterized(List.class, element), "List<" + element.getSimpleName() + ">");
    }

    public static <V> Codec<Map<String, V>> mapOf(Class<V> value) {
        return new Codec<>(parameterized(Map.class, String.class, value), "Map<String, " + value.getSimpleName() + ">");
    }

    /** The value as JSON's tree (maps, lists, strings, numbers): what goes on disk. */
    Object tree(T value) {
        return Json.parse(Json.write(value));
    }

    /** A value from JSON's tree. */
    @SuppressWarnings("unchecked")
    T read(Object tree) {
        return (T) Typed.as(tree, type, name);
    }

    @Override
    public String toString() {
        return "Codec[" + name + "]";
    }

    private static Type parameterized(Class<?> raw, Type... arguments) {
        return new ParameterizedType() {
            @Override public Type[] getActualTypeArguments() { return arguments.clone(); }
            @Override public Type getRawType() { return raw; }
            @Override public Type getOwnerType() { return null; }
        };
    }
}
