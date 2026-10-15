package io.joshuasalcedo.webview4j;

import java.io.ByteArrayOutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

/**
 * Matches a request's method and path against the registered routes. A literal segment beats a
 * {@code {name}} one, so {@code /users/me} wins over {@code /users/{id}}; two routes that would
 * match the same requests are refused when registered.
 */
final class Router {

    private static final Pattern LITERAL = Pattern.compile("[A-Za-z0-9\\-_.~]+");
    private static final Pattern PARAMETER = Pattern.compile("\\{([A-Za-z_][A-Za-z0-9_]*)}");

    private record Compiled(HttpMethod method, String pattern, String[] segments, Route.Handler handler) {
        /** The pattern with parameter names left out: two routes with the same shape clash. */
        String shape() {
            StringBuilder shape = new StringBuilder();
            for (String segment : segments) shape.append('/').append(segment.startsWith("{") ? "{}" : segment);
            return method + " " + shape;
        }

        long literals() {
            long count = 0;
            for (String segment : segments) if (!segment.startsWith("{")) count++;
            return count;
        }
    }

    sealed interface Match {
        record Found(Route.Handler handler, Map<String, String> pathParams) implements Match {}
        record NotAllowed(Set<HttpMethod> allowed) implements Match {}
        record NotFound() implements Match {}
    }

    private final List<Compiled> routes = new CopyOnWriteArrayList<>();

    synchronized void add(Route route) {
        String[] segments = segments(route.pattern());
        for (String segment : segments) {
            if (!LITERAL.matcher(segment).matches() && !PARAMETER.matcher(segment).matches()) {
                throw new IllegalArgumentException("bad segment '" + segment + "' in route " + route.pattern());
            }
        }
        Compiled compiled = new Compiled(route.method(), route.pattern(), segments, route.handler());
        for (Compiled existing : routes) {
            if (existing.shape().equals(compiled.shape())) {
                throw new IllegalStateException(route.method() + " " + route.pattern() + " clashes with " + existing.pattern());
            }
        }
        routes.add(compiled);
    }

    Match match(HttpMethod method, String path) {
        String[] segments;
        try {
            segments = segments(path);
            for (int i = 0; i < segments.length; i++) segments[i] = percentDecode(segments[i]);
        } catch (IllegalArgumentException e) {
            return new Match.NotFound();
        }
        Compiled best = null;
        Map<String, String> bestParams = null;
        Set<HttpMethod> allowed = EnumSet.noneOf(HttpMethod.class);
        for (Compiled route : routes) {
            Map<String, String> params = bind(route, segments);
            if (params == null) continue;
            allowed.add(route.method());
            if (route.method() == method && (best == null || route.literals() > best.literals())) {
                best = route;
                bestParams = params;
            }
        }
        if (best != null) return new Match.Found(best.handler(), bestParams);
        return allowed.isEmpty() ? new Match.NotFound() : new Match.NotAllowed(allowed);
    }

    private static Map<String, String> bind(Compiled route, String[] segments) {
        if (route.segments().length != segments.length) return null;
        Map<String, String> params = new LinkedHashMap<>();
        for (int i = 0; i < segments.length; i++) {
            String expected = route.segments()[i];
            if (expected.startsWith("{")) params.put(expected.substring(1, expected.length() - 1), segments[i]);
            else if (!expected.equals(segments[i])) return null;
        }
        return params;
    }

    private static String[] segments(String path) {
        if (!path.startsWith("/")) throw new IllegalArgumentException("a path starts with /: " + path);
        String trimmed = path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        return trimmed.equals("/") ? new String[0] : trimmed.substring(1).split("/", -1);
    }

    /** RFC 3986 percent-decoding of one path segment ({@code +} stays a plus). */
    static String percentDecode(String segment) {
        if (segment.indexOf('%') < 0) return segment;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (int i = 0; i < segment.length(); i++) {
            char c = segment.charAt(i);
            if (c == '%') {
                if (i + 2 >= segment.length()) throw new IllegalArgumentException("bad escape in " + segment);
                bytes.write(Integer.parseInt(segment, i + 1, i + 3, 16));
                i += 2;
            } else {
                bytes.writeBytes(String.valueOf(c).getBytes(StandardCharsets.UTF_8));
            }
        }
        return bytes.toString(StandardCharsets.UTF_8);
    }

    /** {@code a=1&b=two%20words&a=2} as {@code {a=[1, 2], b=[two words]}}. */
    static Map<String, List<String>> query(String raw) {
        Map<String, List<String>> query = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) return query;
        for (String pair : raw.split("&")) {
            if (pair.isEmpty()) continue;
            int equals = pair.indexOf('=');
            String name = URLDecoder.decode(equals < 0 ? pair : pair.substring(0, equals), StandardCharsets.UTF_8);
            String value = equals < 0 ? "" : URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8);
            query.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
        }
        return query;
    }
}
