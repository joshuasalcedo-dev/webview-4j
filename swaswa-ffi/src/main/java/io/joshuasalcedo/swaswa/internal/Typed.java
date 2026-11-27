package io.joshuasalcedo.swaswa.internal;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON values (as {@link Json} reads them: maps, lists, strings, numbers, booleans, null) as a Java type: a record of
 * the app's own (by its components' names, records and lists inside), an enum (by name), a text, a number, true or
 * false, a list or a map of those. Inside the module only.
 */
public final class Typed {

    private Typed() {}

    /**
     * {@code json} as {@code type}; {@code where} names it in a failure ({@code "users.find.age"}).
     *
     * @throws IllegalArgumentException if it does not fit
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Object as(Object json, Type type, String where) {
        Class<?> raw = type instanceof ParameterizedType p ? (Class<?>) p.getRawType() : (Class<?>) type;
        if (json == null) {
            if (raw.isPrimitive()) throw new IllegalArgumentException(where + " is missing");
            return null;
        }
        if (raw == Object.class) return json;
        if (raw == String.class && json instanceof String) return json;
        if ((raw == long.class || raw == Long.class) && json instanceof Number n) return n.longValue();
        if ((raw == int.class || raw == Integer.class) && json instanceof Number n) return n.intValue();
        if ((raw == double.class || raw == Double.class) && json instanceof Number n) return n.doubleValue();
        if ((raw == float.class || raw == Float.class) && json instanceof Number n) return n.floatValue();
        if ((raw == boolean.class || raw == Boolean.class) && json instanceof Boolean) return json;
        // a page's Date, as JSON writes it (an ISO moment): that moment, or its day where the app runs
        if (raw == java.time.Instant.class && json instanceof String s) return java.time.Instant.parse(s);
        if (raw == java.time.LocalDate.class && json instanceof String s) {
            return s.length() == 10 ? java.time.LocalDate.parse(s) : java.time.LocalDate.ofInstant(java.time.Instant.parse(s), java.time.ZoneId.systemDefault());
        }
        if (raw.isEnum() && json instanceof String s) {
            // by its literal when it has one (a React prop's word: "default"), else by its name
            if (io.joshuasalcedo.swaswa.html.HtmlLiteral.class.isAssignableFrom(raw)) {
                for (Object e : raw.getEnumConstants()) if (((io.joshuasalcedo.swaswa.html.HtmlLiteral) e).literal().equals(s)) return e;
            }
            return Enum.valueOf((Class) raw, s);
        }
        if (Record.class.isAssignableFrom(raw) && json instanceof Map<?, ?> m) return record((Class) raw, (Map<String, Object>) m, where);
        if (raw == List.class && json instanceof List<?> l) {
            Type element = type instanceof ParameterizedType p ? p.getActualTypeArguments()[0] : Object.class;
            List<Object> out = new ArrayList<>();
            for (int i = 0; i < l.size(); i++) out.add(as(l.get(i), element, where + "[" + i + "]"));
            return java.util.Collections.unmodifiableList(out);
        }
        if (raw == Map.class && json instanceof Map<?, ?> m) {
            Type value = type instanceof ParameterizedType p ? p.getActualTypeArguments()[1] : Object.class;
            Map<String, Object> out = new LinkedHashMap<>();
            m.forEach((k, v) -> out.put(String.valueOf(k), as(v, value, where + "." + k)));
            return java.util.Collections.unmodifiableMap(out);
        }
        throw new IllegalArgumentException(where + " must be " + raw.getSimpleName() + ", not " + json);
    }

    /** A record of {@code type} from a JSON object's fields, by its components' names. */
    public static <R extends Record> R record(Class<R> type, Map<String, Object> fields, String where) {
        RecordComponent[] parts = type.getRecordComponents();
        Object[] values = new Object[parts.length];
        Class<?>[] types = new Class<?>[parts.length];
        for (int i = 0; i < parts.length; i++) {
            types[i] = parts[i].getType();
            values[i] = as(fields.get(parts[i].getName()), parts[i].getGenericType(), where + "." + parts[i].getName());
        }
        try {
            var made = type.getDeclaredConstructor(types);
            made.setAccessible(true);
            return made.newInstance(values);
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException(where + ": cannot make a " + type.getSimpleName(), e);
        }
    }
}
