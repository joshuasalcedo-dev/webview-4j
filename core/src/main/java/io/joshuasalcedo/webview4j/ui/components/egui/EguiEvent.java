package io.joshuasalcedo.webview4j.ui.components.egui;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * What the user did on an egui component, as the Rust component tells it: its name ({@code "click"},
 * {@code "view"}, {@code "search"}...) and its fields.
 *
 * @param name   the event
 * @param fields what came with it, as JSON values (numbers are Double or Long, lists, maps)
 */
public record EguiEvent(String name, Map<String, Object> fields) {

    public EguiEvent {
        Objects.requireNonNull(name, "name");
        fields = Map.copyOf(fields == null ? Map.of() : withoutNulls(fields));
    }

    /** A field as a number, or null. */
    public Double number(String field) {
        return fields.get(field) instanceof Number n ? n.doubleValue() : null;
    }

    /** A field as text, or null. */
    public String text(String field) {
        Object v = fields.get(field);
        return v == null ? null : String.valueOf(v);
    }

    /** The event the page sent ({@code {"event": "click", ...}}), or null when it is not one. */
    static EguiEvent of(Object value) {
        if (!(value instanceof Map<?, ?> map) || !(map.get("event") instanceof String name)) return null;
        Map<String, Object> fields = new LinkedHashMap<>();
        map.forEach((k, v) -> {
            if (!"event".equals(k)) fields.put(String.valueOf(k), v);
        });
        return new EguiEvent(name, fields);
    }

    private static Map<String, Object> withoutNulls(Map<String, Object> fields) {
        Map<String, Object> out = new LinkedHashMap<>();
        fields.forEach((k, v) -> {
            if (k != null && v != null) out.put(k, v);
        });
        return out;
    }
}
