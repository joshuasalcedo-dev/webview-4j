package io.joshuasalcedo.webview4j.plugin.devtools;

import io.joshuasalcedo.webview4j.Json;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * An OpenAPI 3 description, as JSON (what Spring's springdoc serves at {@code /v3/api-docs}, Quarkus at
 * {@code /q/openapi?format=json}): its operations, their parameters and bodies, with {@code $ref}s followed.
 */
final class OpenApi {

    record Parameter(String name, String in, boolean required, Map<String, Object> schema) {}

    /** One method on one path. {@code requestSchema} and {@code responseSchema} are null when there is none. */
    record Operation(String method, String path, String summary, String tag, List<Parameter> parameters,
                     Map<String, Object> requestSchema, int successStatus, Map<String, Object> responseSchema) {

        String label() {
            return method + " " + path;
        }
    }

    private static final List<String> METHODS = List.of("get", "post", "put", "patch", "delete", "head", "options");

    final String title;
    final String version;
    /** Where the API is, from the description's {@code servers} (resolved against where it was read), or its origin. */
    final URI server;
    final List<Operation> operations;
    private final Map<String, Object> root;

    private OpenApi(Map<String, Object> root, URI source) {
        this.root = root;
        Map<String, Object> info = map(root.get("info"));
        this.title = text(info.get("title"), "API");
        this.version = text(info.get("version"), "");
        this.server = server(root, source);
        this.operations = operations(root);
    }

    /**
     * Reads a description.
     *
     * @throws IllegalArgumentException if it is not an OpenAPI 3 description in JSON
     */
    static OpenApi parse(String json, URI source) {
        String trimmed = json.stripLeading();
        if (!trimmed.startsWith("{")) {
            throw new IllegalArgumentException("not a JSON OpenAPI description (YAML is not read: Spring serves JSON at /v3/api-docs)");
        }
        Map<String, Object> root = Json.object(json);
        if (!(root.get("openapi") instanceof String v) || !v.startsWith("3")) {
            throw new IllegalArgumentException("not an OpenAPI 3 description (no \"openapi\": \"3...\")");
        }
        return new OpenApi(root, source);
    }

    private static URI server(Map<String, Object> root, URI source) {
        if (root.get("servers") instanceof List<?> servers && !servers.isEmpty()
                && map(servers.getFirst()).get("url") instanceof String url && !url.isBlank()) {
            return source == null ? URI.create(url) : source.resolve(url);
        }
        return source == null ? URI.create("http://localhost:8080/") : source.resolve("/");
    }

    private List<Operation> operations(Map<String, Object> root) {
        List<Operation> list = new ArrayList<>();
        for (Map.Entry<String, Object> path : map(root.get("paths")).entrySet()) {
            Map<String, Object> item = map(path.getValue());
            List<Parameter> shared = parameters(item.get("parameters"));
            for (String method : METHODS) {
                if (!(item.get(method) instanceof Map<?, ?>)) continue;
                Map<String, Object> op = map(item.get(method));
                List<Parameter> parameters = new ArrayList<>(shared);
                for (Parameter p : parameters(op.get("parameters"))) {
                    parameters.removeIf(s -> s.name().equals(p.name()) && s.in().equals(p.in()));
                    parameters.add(p);
                }
                String tag = op.get("tags") instanceof List<?> tags && !tags.isEmpty() ? String.valueOf(tags.getFirst()) : "default";
                String summary = text(op.get("summary"), text(op.get("operationId"), ""));
                Map<String, Object> request = jsonSchema(map(resolve(map(op.get("requestBody")))).get("content"));
                int status = 200;
                Map<String, Object> response = null;
                for (Map.Entry<String, Object> r : map(op.get("responses")).entrySet()) {
                    if (r.getKey().startsWith("2")) {
                        status = r.getKey().matches("\\d{3}") ? Integer.parseInt(r.getKey()) : 200;   // "201", or "2XX"
                        response = jsonSchema(map(resolve(map(r.getValue()))).get("content"));
                        break;
                    }
                }
                list.add(new Operation(method.toUpperCase(Locale.ROOT), path.getKey(), summary, tag, List.copyOf(parameters),
                        request, status, response));
            }
        }
        return List.copyOf(list);
    }

    private List<Parameter> parameters(Object value) {
        List<Parameter> list = new ArrayList<>();
        if (value instanceof List<?> items) {
            for (Object item : items) {
                Map<String, Object> p = resolve(map(item));
                String name = text(p.get("name"), null);
                String in = text(p.get("in"), "query");
                if (name == null || in.equals("cookie")) continue;
                list.add(new Parameter(name, in, Boolean.TRUE.equals(p.get("required")) || in.equals("path"), map(p.get("schema"))));
            }
        }
        return list;
    }

    /** The JSON media type's schema of a {@code content}, or the first one's; null without any. */
    private Map<String, Object> jsonSchema(Object content) {
        Map<String, Object> types = map(content);
        if (types.isEmpty()) return null;
        Object media = types.entrySet().stream().filter(e -> e.getKey().contains("json")).map(Map.Entry::getValue)
                .findFirst().orElse(types.values().iterator().next());
        Map<String, Object> schema = map(map(media).get("schema"));
        return schema.isEmpty() ? null : schema;
    }

    /** Follows {@code $ref}s within the description ({@code #/components/...}). */
    Map<String, Object> resolve(Map<String, Object> node) {
        Map<String, Object> current = node;
        for (int hops = 0; hops < 16 && current.get("$ref") instanceof String ref; hops++) {
            if (!ref.startsWith("#/")) return Map.of();
            Object target = root;
            for (String part : ref.substring(2).split("/")) target = map(target).get(part.replace("~1", "/").replace("~0", "~"));
            current = map(target);
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> m ? (Map<String, Object>) m : new LinkedHashMap<>();
    }

    static String text(Object value, String otherwise) {
        return value instanceof String s && !s.isBlank() ? s : otherwise;
    }
}
