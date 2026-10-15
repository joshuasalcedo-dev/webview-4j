package io.joshuasalcedo.webview4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

/**
 * Turns JSON values and URL text into Java values, for the code the annotation processor
 * generates. Every method takes {@code where}, the place in the request (e.g. {@code body.items[2].name}),
 * and answers a wrong value with a 400 that names it.
 */
public final class Values {

    private Values() {}

    // ---- JSON ----

    /** The body as JSON values; null when empty. */
    public static Object json(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            return Json.parse(text);
        } catch (IllegalArgumentException e) {
            throw new HttpException(400, "the body is not JSON: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(Object value, String where) {
        if (value instanceof Map<?, ?> map) return (Map<String, Object>) map;
        throw wrong(where, "an object", value);
    }

    @SuppressWarnings("unchecked")
    public static List<Object> list(Object value, String where) {
        if (value instanceof List<?> list) return (List<Object>) list;
        throw wrong(where, "a list", value);
    }

    /** Text, or null. */
    public static String string(Object value, String where) {
        if (value == null || value instanceof String) return (String) value;
        throw wrong(where, "text", value);
    }

    public static long longValue(Object value, String where) {
        if (value instanceof Long n) return n;
        throw wrong(where, "a whole number", value);
    }

    public static Long longOrNull(Object value, String where) {
        return value == null ? null : longValue(value, where);
    }

    public static int intValue(Object value, String where) {
        long n = longValue(value, where);
        if (n < Integer.MIN_VALUE || n > Integer.MAX_VALUE) throw wrong(where, "a whole number that fits an int", value);
        return (int) n;
    }

    public static Integer intOrNull(Object value, String where) {
        return value == null ? null : intValue(value, where);
    }

    public static double doubleValue(Object value, String where) {
        if (value instanceof Number n) return n.doubleValue();
        throw wrong(where, "a number", value);
    }

    public static Double doubleOrNull(Object value, String where) {
        return value == null ? null : doubleValue(value, where);
    }

    public static boolean booleanValue(Object value, String where) {
        if (value instanceof Boolean b) return b;
        throw wrong(where, "true or false", value);
    }

    public static Boolean booleanOrNull(Object value, String where) {
        return value == null ? null : booleanValue(value, where);
    }

    /** An enum constant by its name, or null. */
    public static <E extends Enum<E>> E enumValue(Class<E> type, Object value, String where) {
        if (value == null) return null;
        if (value instanceof String name) {
            for (E constant : type.getEnumConstants()) {
                if (constant.name().equals(name)) return constant;
            }
        }
        throw wrong(where, "one of " + List.of(type.getEnumConstants()), value);
    }

    /** A list, each element read by {@code read}; null stays null. */
    public static <T> List<T> listOf(Object value, String where, BiFunction<Object, String, T> read) {
        if (value == null) return null;
        List<Object> items = list(value, where);
        List<T> out = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) out.add(read.apply(items.get(i), where + "[" + i + "]"));
        return out;
    }

    /** An object as a map, each value read by {@code read}; null stays null. */
    public static <T> Map<String, T> mapOf(Object value, String where, BiFunction<Object, String, T> read) {
        if (value == null) return null;
        Map<String, T> out = new LinkedHashMap<>();
        object(value, where).forEach((key, item) -> out.put(key, read.apply(item, where + "." + key)));
        return out;
    }

    public static <T> Optional<T> optional(Object value, String where, BiFunction<Object, String, T> read) {
        return value == null ? Optional.empty() : Optional.ofNullable(read.apply(value, where));
    }

    // ---- path and query text ----

    public static String required(String raw, String where) {
        if (raw == null) throw new HttpException(400, where + " is required");
        return raw;
    }

    public static long parseLong(String raw, String where) {
        try {
            return Long.parseLong(required(raw, where));
        } catch (NumberFormatException e) {
            throw wrong(where, "a whole number", raw);
        }
    }

    public static Long parseLongOrNull(String raw, String where) {
        return raw == null ? null : parseLong(raw, where);
    }

    public static int parseInt(String raw, String where) {
        try {
            return Integer.parseInt(required(raw, where));
        } catch (NumberFormatException e) {
            throw wrong(where, "a whole number that fits an int", raw);
        }
    }

    public static Integer parseIntOrNull(String raw, String where) {
        return raw == null ? null : parseInt(raw, where);
    }

    public static double parseDouble(String raw, String where) {
        try {
            return Double.parseDouble(required(raw, where));
        } catch (NumberFormatException e) {
            throw wrong(where, "a number", raw);
        }
    }

    public static Double parseDoubleOrNull(String raw, String where) {
        return raw == null ? null : parseDouble(raw, where);
    }

    public static boolean parseBoolean(String raw, String where) {
        return switch (required(raw, where).toLowerCase(Locale.ROOT)) {
            case "true" -> true;
            case "false" -> false;
            default -> throw wrong(where, "true or false", raw);
        };
    }

    public static Boolean parseBooleanOrNull(String raw, String where) {
        return raw == null ? null : parseBoolean(raw, where);
    }

    public static <E extends Enum<E>> E parseEnum(Class<E> type, String raw, String where) {
        return raw == null ? null : enumValue(type, raw, where);
    }

    private static HttpException wrong(String where, String expected, Object value) {
        String shown = value instanceof String s ? "\"" + s + "\"" : String.valueOf(value);
        if (shown.length() > 60) shown = shown.substring(0, 60) + "…";
        return new HttpException(400, where + " must be " + expected + ", not " + shown);
    }
}
