package io.joshuasalcedo.webview4j.ui.state;

import io.joshuasalcedo.webview4j.Json;
import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * How a value is kept on disk and read back: records by their components, lists, sets and maps of them, enums by
 * name, numbers, text, yes/no, {@code java.time} values, UUIDs, and value objects ({@code Money}, {@code Email}: any
 * class with a static {@code parse(String)}, {@code of(String)} or {@code valueOf(String)}) by their text.
 * <pre>{@code
 * Codec.of(Cart.class)              // a record
 * Codec.listOf(Order.class)         // List<Order>
 * Codec.of(new Codec.Type<Map<String, List<Order>>>() {})   // anything else, by its full type
 * }</pre>
 */
public final class Codec<T> {

    /** A full generic type, kept by an anonymous subclass: {@code new Codec.Type<Map<String, Order>>() {}}. */
    public abstract static class Type<T> {
        final java.lang.reflect.Type type;

        protected Type() {
            this.type = ((ParameterizedType) getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        }
    }

    private final java.lang.reflect.Type type;

    private Codec(java.lang.reflect.Type type) {
        this.type = Objects.requireNonNull(type, "type");
    }

    public static <T> Codec<T> of(Class<T> type) {
        return new Codec<>(type);
    }

    public static <T> Codec<T> of(Type<T> type) {
        return new Codec<>(type.type);
    }

    public static <E> Codec<List<E>> listOf(Class<E> element) {
        return new Codec<>(parameterized(List.class, element));
    }

    public static <V> Codec<Map<String, V>> mapOf(Class<V> value) {
        return new Codec<>(parameterized(Map.class, String.class, value));
    }

    /** The value as bytes (JSON, UTF-8). */
    public byte[] encode(T value) {
        return Json.write(tree(value)).getBytes(StandardCharsets.UTF_8);
    }

    public T decode(byte[] bytes) {
        return read(Json.parse(new String(bytes, StandardCharsets.UTF_8)));
    }

    /** The value as JSON's maps, lists and plain values. */
    Object tree(Object value) {
        return toTree(value);
    }

    @SuppressWarnings("unchecked")
    T read(Object tree) {
        return (T) fromTree(tree, type);
    }

    // ---- to a tree ----

    static Object toTree(Object v) {
        return switch (v) {
            case null -> null;
            case String s -> s;
            case Boolean b -> b;
            case BigDecimal d -> d.toPlainString();
            case BigInteger i -> i.toString();
            case Number n -> n;
            case Character c -> String.valueOf(c);
            case Enum<?> e -> e.name();
            case Optional<?> o -> toTree(o.orElse(null));
            case Map<?, ?> m -> {
                Map<String, Object> out = new LinkedHashMap<>();
                m.forEach((k, x) -> out.put(k instanceof Enum<?> e ? e.name() : String.valueOf(k), toTree(x)));
                yield out;
            }
            case Collection<?> c -> {
                List<Object> out = new ArrayList<>(c.size());
                for (Object x : c) out.add(toTree(x));
                yield out;
            }
            case Record r -> {
                Map<String, Object> out = new LinkedHashMap<>();
                for (RecordComponent c : r.getClass().getRecordComponents()) out.put(c.getName(), toTree(component(r, c)));
                yield out;
            }
            default -> {
                if (v.getClass().isArray()) {
                    List<Object> out = new ArrayList<>();
                    for (int i = 0; i < Array.getLength(v); i++) out.add(toTree(Array.get(v, i)));
                    yield out;
                }
                yield v.toString();   // java.time, UUID, value objects: their text, read back with parse
            }
        };
    }

    private static Object component(Record r, RecordComponent c) {
        try {
            Method m = c.getAccessor();
            m.setAccessible(true);
            return m.invoke(r);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new IllegalStateException("the " + c.getName() + " of " + r.getClass().getName() + " could not be read", e);
        }
    }

    // ---- from a tree ----

    static Object fromTree(Object tree, java.lang.reflect.Type type) {
        if (type instanceof ParameterizedType p) return parameterized(tree, p);
        if (type instanceof GenericArrayType g) return array(tree, raw(g.getGenericComponentType()), g.getGenericComponentType());
        if (!(type instanceof Class<?> c)) return tree;
        if (tree == null) return c.isPrimitive() ? primitiveZero(c) : null;
        if (c == Object.class) return tree;
        if (c == String.class) return String.valueOf(tree);
        if (c == boolean.class || c == Boolean.class) return tree instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(tree));
        if (c == int.class || c == Integer.class) return number(tree).intValue();
        if (c == long.class || c == Long.class) return number(tree).longValue();
        if (c == double.class || c == Double.class) return number(tree).doubleValue();
        if (c == float.class || c == Float.class) return number(tree).floatValue();
        if (c == short.class || c == Short.class) return number(tree).shortValue();
        if (c == byte.class || c == Byte.class) return number(tree).byteValue();
        if (c == char.class || c == Character.class) return String.valueOf(tree).charAt(0);
        if (c == BigDecimal.class) return new BigDecimal(String.valueOf(tree));
        if (c == BigInteger.class) return new BigInteger(String.valueOf(tree));
        if (c == UUID.class) return UUID.fromString(String.valueOf(tree));
        if (c.isEnum()) return enumOf(c, String.valueOf(tree));
        if (c.isArray()) return array(tree, c.getComponentType(), c.getComponentType());
        if (c.isRecord()) return record(tree, c);
        if (List.class.isAssignableFrom(c) || Collection.class == c) return new ArrayList<>((List<?>) tree);
        if (Map.class.isAssignableFrom(c)) return new LinkedHashMap<>((Map<?, ?>) tree);
        return parse(c, String.valueOf(tree));
    }

    private static Object parameterized(Object tree, ParameterizedType p) {
        Class<?> raw = raw(p);
        java.lang.reflect.Type[] args = p.getActualTypeArguments();
        if (tree == null) return raw == Optional.class ? Optional.empty() : null;
        if (raw == Optional.class) return Optional.ofNullable(fromTree(tree, args[0]));
        if (Collection.class.isAssignableFrom(raw)) {
            Collection<Object> out = Set.class.isAssignableFrom(raw) ? new LinkedHashSet<>() : new ArrayList<>();
            for (Object x : (List<?>) tree) out.add(fromTree(x, args[0]));
            return Set.class.isAssignableFrom(raw) ? Collections.unmodifiableSet(out) : Collections.unmodifiableList(out);
        }
        if (Map.class.isAssignableFrom(raw)) {
            Map<Object, Object> out = new LinkedHashMap<>();
            ((Map<?, ?>) tree).forEach((k, x) -> out.put(fromTree(k, args[0]), fromTree(x, args[1])));
            return Collections.unmodifiableMap(out);
        }
        if (raw.isRecord()) return record(tree, raw);
        return fromTree(tree, raw);
    }

    private static Object record(Object tree, Class<?> type) {
        Map<?, ?> m = (Map<?, ?>) tree;
        RecordComponent[] components = type.getRecordComponents();
        Class<?>[] types = new Class<?>[components.length];
        Object[] args = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            types[i] = components[i].getType();
            args[i] = fromTree(m.get(components[i].getName()), components[i].getGenericType());
        }
        try {
            var constructor = type.getDeclaredConstructor(types);
            constructor.setAccessible(true);
            return constructor.newInstance(args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("a " + type.getName() + " could not be made from what was kept", e);
        }
    }

    private static Object array(Object tree, Class<?> element, java.lang.reflect.Type generic) {
        List<?> list = (List<?>) tree;
        Object out = Array.newInstance(element, list.size());
        for (int i = 0; i < list.size(); i++) Array.set(out, i, fromTree(list.get(i), generic));
        return out;
    }

    private static final Map<Class<?>, Method> PARSERS = new ConcurrentHashMap<>();

    /** A value from its text: {@code parse(CharSequence|String)}, {@code of(String)} or {@code valueOf(String)}. */
    private static Object parse(Class<?> type, String text) {
        Method m = PARSERS.computeIfAbsent(type, Codec::parser);
        try {
            return m.invoke(null, text);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("a " + type.getName() + " could not be read from \"" + text + "\"", e.getCause() != null ? e.getCause() : e);
        }
    }

    private static Method parser(Class<?> type) {
        for (String name : List.of("parse", "of", "valueOf", "fromString")) {
            for (Class<?> arg : List.of(CharSequence.class, String.class)) {
                try {
                    Method m = type.getMethod(name, arg);
                    if (Modifier.isStatic(m.getModifiers()) && type.isAssignableFrom(m.getReturnType())) return m;
                } catch (NoSuchMethodException e) {
                    // the next one
                }
            }
        }
        throw new IllegalArgumentException(type.getName() + " cannot be kept: give it a static parse(String), or keep a record of its parts");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumOf(Class<?> type, String name) {
        return Enum.valueOf((Class) type, name);
    }

    private static Number number(Object tree) {
        return tree instanceof Number n ? n : new BigDecimal(String.valueOf(tree));
    }

    private static Object primitiveZero(Class<?> c) {
        if (c == boolean.class) return false;
        if (c == char.class) return '\0';
        return fromTree(0, c);
    }

    private static Class<?> raw(java.lang.reflect.Type t) {
        if (t instanceof Class<?> c) return c;
        if (t instanceof ParameterizedType p) return (Class<?>) p.getRawType();
        if (t instanceof GenericArrayType g) return Array.newInstance(raw(g.getGenericComponentType()), 0).getClass();
        return Object.class;
    }

    private static ParameterizedType parameterized(Class<?> raw, java.lang.reflect.Type... args) {
        return new ParameterizedType() {
            @Override public java.lang.reflect.Type[] getActualTypeArguments() { return args.clone(); }
            @Override public java.lang.reflect.Type getRawType() { return raw; }
            @Override public java.lang.reflect.Type getOwnerType() { return null; }
        };
    }

    private static final class Collections {
        static <E> List<E> unmodifiableList(Collection<E> c) { return java.util.Collections.unmodifiableList(new ArrayList<>(c)); }
        static <E> Set<E> unmodifiableSet(Collection<E> c) { return java.util.Collections.unmodifiableSet(new LinkedHashSet<>(c)); }
        static <K, V> Map<K, V> unmodifiableMap(Map<K, V> m) { return java.util.Collections.unmodifiableMap(m); }
    }
}
