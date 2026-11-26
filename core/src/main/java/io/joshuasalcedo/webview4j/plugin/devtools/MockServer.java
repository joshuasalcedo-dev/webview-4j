package io.joshuasalcedo.webview4j.plugin.devtools;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.joshuasalcedo.webview4j.Json;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * A server that answers an OpenAPI description's endpoints with example data made from their schemas, without the
 * real server: for building a frontend before the backend exists, or while it is down. On this machine only, on a
 * free port; every answer allows any origin, so a page anywhere can call it.
 * <pre>{@code
 * try (MockServer mock = MockServer.start(Files.readString(Path.of("openapi.json")))) {
 *     System.out.println(mock.url());   // http://127.0.0.1:52144/
 * }
 * }</pre>
 */
public final class MockServer implements AutoCloseable {

    private record Route(String method, Pattern path, OpenApi.Operation operation) {}

    private final OpenApi api;
    private final List<Route> routes;
    private final HttpServer server;

    private MockServer(OpenApi api) {
        this.api = api;
        this.routes = api.operations.stream()
                .map(op -> new Route(op.method(), template(op.path()), op))
                .toList();
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException("could not start the mock server", e);
        }
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/", this::answer);
        server.start();
    }

    /**
     * Starts answering the endpoints {@code description} has: an OpenAPI 3 description in JSON.
     *
     * @throws IllegalArgumentException if it is not one
     */
    public static MockServer start(String description) {
        return new MockServer(OpenApi.parse(Objects.requireNonNull(description, "description"), null));
    }

    static MockServer start(OpenApi api) {
        return new MockServer(api);
    }

    /** Where it answers: {@code http://127.0.0.1:<port>/}, and the description's paths under it. */
    public URI url() {
        return URI.create("http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort() + "/");
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void answer(HttpExchange exchange) throws IOException {
        try (exchange) {
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS");
            String method = exchange.getRequestMethod();
            if (method.equals("OPTIONS")) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            String path = exchange.getRequestURI().getPath();
            Route route = routes.stream().filter(r -> r.method().equals(method) && r.path().matcher(path).matches()).findFirst().orElse(null);
            if (route == null) {
                boolean other = routes.stream().anyMatch(r -> r.path().matcher(path).matches());
                send(exchange, other ? 405 : 404, "{\"error\":\"" + (other ? "method not allowed" : "no endpoint " + path) + "\"}");
                return;
            }
            exchange.getRequestBody().readAllBytes();
            OpenApi.Operation op = route.operation();
            Object example = Examples.of(api, op.responseSchema(), null);
            int status = op.successStatus();
            if (example == null || status == 204) {
                exchange.sendResponseHeaders(status == 200 && example == null ? 204 : status, -1);
            } else {
                send(exchange, status, Json.write(example));
            }
        }
    }

    private static void send(HttpExchange exchange, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }

    /** A path template ({@code /users/{id}}) as a pattern: each {@code {name}} one path segment. */
    static Pattern template(String path) {
        StringBuilder regex = new StringBuilder();
        for (String part : path.split("(?=\\{)|(?<=})")) {
            regex.append(part.startsWith("{") && part.endsWith("}") ? "[^/]+" : Pattern.quote(part));
        }
        return Pattern.compile(regex + "/?");
    }
}
