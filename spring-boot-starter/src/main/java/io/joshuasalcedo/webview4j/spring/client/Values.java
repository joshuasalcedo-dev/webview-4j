package io.joshuasalcedo.webview4j.spring.client;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * What a person typed, as the Java type a method takes: numbers, booleans, enums, dates and times, UUIDs, lists
 * (one item per line or comma), {@code Optional}s, and any type with a static {@code parse}, {@code valueOf},
 * {@code of}, {@code from} or {@code fromString} taking a {@code String}, or a constructor taking one (value objects
 * such as an {@code Email} or {@code Money}). A value that does not fit fails with a message for people.
 */
final class Values {

    private static final List<String> FACTORIES = List.of("parse", "valueOf", "of", "from", "fromString");

    private Values() {}

    /** Thrown when typed text does not fit its type: its message is for people. */
    static final class Unfit extends RuntimeException {
        Unfit(String message) {
            super(message);
        }
    }

    /** Whether {@code type} is one typed text can be (not a record or a bean of several fields). */
    static boolean simple(Class<?> type) {
        return type.isPrimitive() || type == String.class || Number.class.isAssignableFrom(type) || type == Boolean.class
                || type == Character.class || type.isEnum() || type == UUID.class || type.getName().startsWith("java.time.")
                || (!type.isRecord() && factory(type) != null);
    }

    /** {@code text} as {@code type}; null for empty text (or the primitive's zero, or an empty list or Optional). */
    static Object convert(String text, Type type) {
        Class<?> raw = raw(type);
        String t = text == null ? "" : text.strip();
        if (raw == Optional.class) return t.isEmpty() ? Optional.empty() : Optional.ofNullable(convert(t, argument(type)));
        if (List.class.isAssignableFrom(raw) || Set.class.isAssignableFrom(raw) || raw == Collection.class) {
            Collection<Object> items = Set.class.isAssignableFrom(raw) ? new LinkedHashSet<>() : new ArrayList<>();
            for (String part : t.split("\\R|,")) if (!part.isBlank()) items.add(convert(part, argument(type)));
            return items instanceof List<?> ? List.copyOf(items) : Set.copyOf(items);
        }
        if (t.isEmpty()) return raw.isPrimitive() ? zero(raw) : null;
        try {
            if (raw == String.class) return text;
            if (raw == int.class || raw == Integer.class) return Integer.valueOf(t);
            if (raw == long.class || raw == Long.class) return Long.valueOf(t);
            if (raw == short.class || raw == Short.class) return Short.valueOf(t);
            if (raw == byte.class || raw == Byte.class) return Byte.valueOf(t);
            if (raw == double.class || raw == Double.class) return Double.valueOf(t);
            if (raw == float.class || raw == Float.class) return Float.valueOf(t);
            if (raw == BigDecimal.class) return new BigDecimal(t);
            if (raw == BigInteger.class) return new BigInteger(t);
            if (raw == boolean.class || raw == Boolean.class) return switch (t.toLowerCase(Locale.ROOT)) {
                case "true", "yes", "on", "1" -> true;
                case "false", "no", "off", "0" -> false;
                default -> throw new Unfit("Yes or no");
            };
            if (raw == char.class || raw == Character.class) {
                if (t.length() != 1) throw new Unfit("One character");
                return t.charAt(0);
            }
            if (raw.isEnum()) {
                for (Object constant : raw.getEnumConstants()) {
                    if (((Enum<?>) constant).name().equalsIgnoreCase(t) || constant.toString().equalsIgnoreCase(t)) return constant;
                }
                throw new Unfit("One of " + List.of(raw.getEnumConstants()));
            }
            if (raw == UUID.class) return UUID.fromString(t);
            if (raw == LocalDate.class) return LocalDate.parse(t);
            if (raw == LocalDateTime.class) return LocalDateTime.parse(t);
            if (raw == LocalTime.class) return LocalTime.parse(t);
            if (raw == Instant.class) return Instant.parse(t);
            if (raw == OffsetDateTime.class) return OffsetDateTime.parse(t);
            if (raw == ZonedDateTime.class) return ZonedDateTime.parse(t);
            if (raw == Duration.class) return Duration.parse(t);
        } catch (NumberFormatException e) {
            throw new Unfit(raw == int.class || raw == Integer.class || raw == long.class || raw == Long.class
                    || raw == short.class || raw == Short.class || raw == byte.class || raw == Byte.class || raw == BigInteger.class
                    ? "A whole number" : "A number");
        } catch (java.time.format.DateTimeParseException e) {
            throw new Unfit(raw == LocalDate.class ? "A date, as 2026-11-25" : raw == Duration.class ? "A duration, as PT15M" : "A date and time, as 2026-11-25T09:30:00");
        } catch (IllegalArgumentException e) {
            if (raw == UUID.class) throw new Unfit("An id, as 3fa85f64-5717-4562-b3fc-2c963f66afa6");
            throw e;
        }
        java.lang.reflect.Executable factory = factory(raw);
        if (factory == null) throw new Unfit("This can't be typed in (" + raw.getSimpleName() + ")");
        try {
            Object value = factory instanceof Method m ? m.invoke(null, t) : ((Constructor<?>) factory).newInstance(t);
            return value instanceof Optional<?> o ? o.orElseThrow(() -> new Unfit("Not a valid " + words(raw.getSimpleName()))) : value;
        } catch (InvocationTargetException e) {
            // The type's own message says what is wrong (a value object's is written for people).
            Throwable cause = e.getCause();
            throw new Unfit(cause != null && cause.getMessage() != null ? cause.getMessage() : "Not a valid " + words(raw.getSimpleName()));
        } catch (ReflectiveOperationException e) {
            throw new Unfit("Not a valid " + words(raw.getSimpleName()));
        }
    }

    /** A value as people read it again in a field (for a default or an answer). */
    static String text(Object value) {
        if (value == null) return "";
        if (value instanceof Optional<?> o) return o.map(Values::text).orElse("");
        if (value instanceof Collection<?> c) return String.join(", ", c.stream().map(Values::text).toList());
        return String.valueOf(value);
    }

    /** The static factory or String constructor that makes {@code type} from text; null without one. */
    static java.lang.reflect.Executable factory(Class<?> type) {
        for (String name : FACTORIES) {
            try {
                Method m = type.getMethod(name, String.class);
                if (Modifier.isStatic(m.getModifiers()) && (type.isAssignableFrom(m.getReturnType()) || m.getReturnType() == Optional.class)) return m;
            } catch (NoSuchMethodException ignored) {
                // the next name
            }
            try {
                Method m = type.getMethod(name, CharSequence.class);
                if (Modifier.isStatic(m.getModifiers()) && type.isAssignableFrom(m.getReturnType())) return m;
            } catch (NoSuchMethodException ignored) {
                // the next name
            }
        }
        try {
            return type.getConstructor(String.class);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    static Class<?> raw(Type type) {
        if (type instanceof Class<?> c) return c;
        if (type instanceof ParameterizedType p) return (Class<?>) p.getRawType();
        return Object.class;
    }

    static Type argument(Type type) {
        return type instanceof ParameterizedType p && p.getActualTypeArguments().length > 0 ? p.getActualTypeArguments()[0] : String.class;
    }

    private static Object zero(Class<?> primitive) {
        if (primitive == boolean.class) return false;
        if (primitive == char.class) return '\0';
        if (primitive == double.class) return 0d;
        if (primitive == float.class) return 0f;
        if (primitive == long.class) return 0L;
        if (primitive == short.class) return (short) 0;
        if (primitive == byte.class) return (byte) 0;
        return 0;
    }

    /** {@code getUsers} as "Get users", {@code displayName} as "Display name", {@code ManagementAPI} as "Management". */
    static String words(String name) {
        String trimmed = name.replaceAll("(API|Api|Client|Service|Exchange)$", "");
        if (trimmed.isEmpty()) trimmed = name;
        String spaced = trimmed.replaceAll("([a-z0-9])([A-Z])", "$1 $2").replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2").replace('_', ' ');
        String lower = spaced.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
