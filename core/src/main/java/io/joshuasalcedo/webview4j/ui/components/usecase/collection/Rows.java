package io.joshuasalcedo.webview4j.ui.components.usecase.collection;

import io.joshuasalcedo.valueobject.financial.Money;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** How the collections send Java's data to the page: records and maps as rows, values as the page shows them. */
final class Rows {

    private Rows() {}

    /** A record (its components), a map, or anything else (as "value"): one row. */
    static Map<String, Object> row(Object row) {
        Map<String, Object> fields = new LinkedHashMap<>();
        if (row instanceof Map<?, ?> map) {
            map.forEach((k, v) -> put(fields, String.valueOf(k), v));
        } else if (row instanceof Record record) {
            for (RecordComponent c : record.getClass().getRecordComponents()) put(fields, c.getName(), read(record, c));
        } else {
            fields.put("value", row == null ? null : String.valueOf(row));
        }
        return fields;
    }

    static List<Map<String, Object>> rows(Collection<?> rows) {
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (Object row : rows) out.add(row(row));
        return out;
    }

    /**
     * One field of a row: money as its smallest unit (and its currency beside it, as {@code <key>Currency}), numbers,
     * yes/no and text as they are, enums by name, dates and times as ISO text, lists of records as rows (a tree's
     * children), other value objects as their text.
     */
    static void put(Map<String, Object> fields, String key, Object value) {
        switch (value) {
            case null -> fields.put(key, null);
            case Money m -> {
                fields.put(key, m.amount().movePointRight(m.currency().decimalPlaces()).longValue());
                fields.put(key + "Currency", m.currency().code());
            }
            case BigDecimal d -> fields.put(key, d.doubleValue());
            case Number n -> fields.put(key, n);
            case Boolean b -> fields.put(key, b);
            case String s -> fields.put(key, s);
            case Enum<?> e -> fields.put(key, e.name());
            case Temporal t -> fields.put(key, t.toString());
            case Optional<?> o -> put(fields, key, o.orElse(null));
            case Collection<?> c -> {
                List<Object> list = new ArrayList<>(c.size());
                for (Object item : c) list.add(item instanceof Record || item instanceof Map<?, ?> ? row(item) : value(item));
                fields.put(key, list);
            }
            default -> fields.put(key, value.toString());
        }
    }

    /**
     * Any value as JSON a property grid shows: maps and lists kept (their values too), records as maps, numbers,
     * yes/no and text as they are, money and other value objects as their text.
     */
    static Object value(Object value) {
        return switch (value) {
            case null -> null;
            case Map<?, ?> m -> {
                Map<String, Object> out = new LinkedHashMap<>();
                m.forEach((k, v) -> out.put(String.valueOf(k), value(v)));
                yield out;
            }
            case Collection<?> c -> {
                List<Object> out = new ArrayList<>(c.size());
                for (Object item : c) out.add(value(item));
                yield out;
            }
            case Record r -> {
                Map<String, Object> out = new LinkedHashMap<>();
                for (RecordComponent c : r.getClass().getRecordComponents()) out.put(c.getName(), value(read(r, c)));
                yield out;
            }
            case Optional<?> o -> value(o.orElse(null));
            case BigDecimal d -> d.doubleValue();
            case Number n -> n;
            case Boolean b -> b;
            case String s -> s;
            case Enum<?> e -> e.name();
            default -> value.toString();
        };
    }

    private static Object read(Record record, RecordComponent c) {
        try {
            var accessor = c.getAccessor();
            accessor.setAccessible(true);
            return accessor.invoke(record);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    static List<String> texts(Object value) {
        List<String> list = new ArrayList<>();
        if (value instanceof List<?> items) for (Object item : items) list.add(String.valueOf(item));
        return List.copyOf(list);
    }

    static String words(String name) {
        String spaced = name.replaceAll("([a-z0-9])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT);
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }
}
