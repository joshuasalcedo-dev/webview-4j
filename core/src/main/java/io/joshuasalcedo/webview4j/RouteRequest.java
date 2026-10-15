package io.joshuasalcedo.webview4j;

import java.util.List;
import java.util.Map;

/**
 * A page's request to a route: which view it came from (set by the native side, never by the
 * page), the method and path, the {@code {name}} segments it matched, the query, and the body.
 * A controller method can take it, or the {@link View}, as a parameter.
 */
public record RouteRequest(View view, HttpMethod method, String path, Map<String, String> pathParams,
                           Map<String, List<String>> query, String body) {

    public RouteRequest {
        pathParams = Map.copyOf(pathParams);
        query = Map.copyOf(query);
    }

    /** A matched {@code {name}} segment, decoded. */
    public String pathParam(String name) {
        return pathParams.get(name);
    }

    /** The first value of query parameter {@code name}, decoded; null when absent. */
    public String query(String name) {
        List<String> values = query.get(name);
        return values == null || values.isEmpty() ? null : values.getFirst();
    }

    /** The body as JSON values; null when empty. Malformed JSON answers 400. */
    public Object json() {
        return Values.json(body);
    }
}
