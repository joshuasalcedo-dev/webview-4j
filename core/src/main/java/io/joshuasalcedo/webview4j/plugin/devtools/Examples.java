package io.joshuasalcedo.webview4j.plugin.devtools;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Example values from schemas: the schema's own {@code example}, {@code default} or first {@code enum} value when
 * it has one, otherwise one that fits its type and format (an email for {@code format: email}, a date-time for
 * {@code date-time}...). For "try it" forms, the mock server's answers and running every endpoint.
 */
final class Examples {

    private static final int DEPTH = 6;

    private Examples() {}

    static Object of(OpenApi api, Map<String, Object> schema, String name) {
        return of(api, schema, name, 0);
    }

    private static Object of(OpenApi api, Map<String, Object> raw, String name, int depth) {
        if (raw == null) return null;
        Map<String, Object> schema = api.resolve(raw);
        if (schema.containsKey("example")) return schema.get("example");
        if (schema.get("examples") instanceof List<?> examples && !examples.isEmpty()) return examples.getFirst();
        if (schema.containsKey("default")) return schema.get("default");
        if (schema.get("enum") instanceof List<?> values && !values.isEmpty()) return values.getFirst();
        if (schema.containsKey("const")) return schema.get("const");
        for (String combined : List.of("allOf", "oneOf", "anyOf")) {
            if (schema.get(combined) instanceof List<?> parts && !parts.isEmpty()) {
                if (combined.equals("allOf")) {
                    Map<String, Object> merged = new LinkedHashMap<>();
                    for (Object part : parts) if (of(api, OpenApi.map(part), name, depth + 1) instanceof Map<?, ?> m) m.forEach((k, v) -> merged.put(String.valueOf(k), v));
                    return merged;
                }
                return of(api, OpenApi.map(parts.getFirst()), name, depth + 1);
            }
        }
        if (depth > DEPTH) return null;
        String type = type(schema);
        String format = OpenApi.text(schema.get("format"), "");
        return switch (type) {
            case "object" -> {
                Map<String, Object> object = new LinkedHashMap<>();
                for (Map.Entry<String, Object> p : OpenApi.map(schema.get("properties")).entrySet()) {
                    object.put(p.getKey(), of(api, OpenApi.map(p.getValue()), p.getKey(), depth + 1));
                }
                yield object;
            }
            case "array" -> {
                List<Object> list = new ArrayList<>();
                Object item = of(api, OpenApi.map(schema.get("items")), name, depth + 1);
                if (item != null) list.add(item);
                yield list;
            }
            case "integer" -> schema.get("minimum") instanceof Number min ? min.longValue() : 1L;
            case "number" -> schema.get("minimum") instanceof Number min ? min.doubleValue() : 1.5;
            case "boolean" -> true;
            default -> text(format, name);
        };
    }

    private static String type(Map<String, Object> schema) {
        Object type = schema.get("type");
        if (type instanceof List<?> types) type = types.stream().<Object>map(t -> t).filter(t -> !"null".equals(t)).findFirst().orElse("string");
        if (type instanceof String t) return t;
        if (schema.containsKey("properties")) return "object";
        if (schema.containsKey("items")) return "array";
        return "string";
    }

    private static String text(String format, String name) {
        String n = name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
        return switch (format) {
            case "date-time" -> OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS).toString();
            case "date" -> LocalDate.now().toString();
            case "email" -> "ada@example.com";
            case "uuid" -> "3fa85f64-5717-4562-b3fc-2c963f66afa6";
            case "uri", "url" -> "https://example.com";
            case "ipv4" -> "192.168.0.1";
            case "password" -> "secret";
            case "byte" -> "aGVsbG8=";
            default -> n.contains("email") ? "ada@example.com"
                    : n.contains("name") ? "Ada Lovelace"
                    : n.contains("phone") ? "+63 912 345 6789"
                    : n.contains("url") ? "https://example.com"
                    : n.isEmpty() ? "text" : n;
        };
    }
}
