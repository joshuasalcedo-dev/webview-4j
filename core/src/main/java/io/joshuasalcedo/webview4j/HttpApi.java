package io.joshuasalcedo.webview4j;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The same {@code @WebViewController} routes as a plain HTTP API: {@code GET /api/users/42} runs
 * the route {@code GET /users/42}. The generated TypeScript client calls it with {@code fetch}
 * once its base URL is set, so the screens work against it unchanged: from another desktop shell
 * such as Tauri, from a browser, or from a server later.
 * <pre>{@code
 * try (HttpApi api = HttpApi.local().token(secret).origins("http://tauri.localhost")
 *         .register(new UsersController()).start()) {
 *     System.out.println(api.port());
 * }
 * }</pre>
 *
 * <p>Answers are as on the custom protocol: {@code 200} with the route's value as JSON,
 * {@code 204} for none, an {@link HttpException}'s status, {@code 400} for an
 * {@link IllegalArgumentException}, {@code 500} otherwise, with the message as text. With a
 * {@link #token token}, every request must carry {@code Authorization: Bearer <token>}: a port on
 * this machine is reachable by every program on it. Each request runs on a virtual thread.
 */
public final class HttpApi implements AutoCloseable {

    private static final System.Logger LOG = System.getLogger(HttpApi.class.getName());
    private static final String PREFIX = "/api";
    private static final int MAX_BODY = 64 * 1024 * 1024;

    private final Router router = new Router();
    private InetSocketAddress address;
    private String token;
    private Set<String> origins = Set.of();
    private HttpServer server;
    private ExecutorService threads;

    private HttpApi(InetSocketAddress address) {
        this.address = address;
    }

    /** On this machine only (the loopback address), on a free port. */
    public static HttpApi local() {
        return new HttpApi(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
    }

    /** On {@code address}, e.g. every interface on port 8080 for a server. */
    public static HttpApi on(InetSocketAddress address) {
        return new HttpApi(Objects.requireNonNull(address, "address"));
    }

    /** Requires {@code Authorization: Bearer <token>} on every request; null for none. */
    public HttpApi token(String token) {
        if (token != null && token.length() < 16) throw new IllegalArgumentException("a token is at least 16 characters");
        this.token = token;
        return this;
    }

    /** The origins whose pages may call it from a browser (CORS), such as {@code http://tauri.localhost}. */
    public HttpApi origins(String... origins) {
        this.origins = Set.of(origins);
        return this;
    }

    /**
     * Serves a {@code @WebViewController}'s routes, from the route table the annotation processor
     * generated for its class.
     *
     * @throws IllegalArgumentException if no route table was generated for the controller's class
     * @throws IllegalStateException if a route clashes with one already registered
     */
    @SuppressWarnings("unchecked")
    public <T> HttpApi register(T controller) {
        Objects.requireNonNull(controller, "controller");
        Class<T> type = (Class<T>) controller.getClass();
        Routes<T> routes;
        try {
            routes = (Routes<T>) Class.forName(type.getName() + "_Routes", true, type.getClassLoader())
                    .getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("no route table for " + type.getName()
                    + ": is it a @WebViewController compiled with the webview-4j processor?", e);
        }
        return register(routes, controller);
    }

    /** Serves these routes, calling {@code controller}. */
    public <T> HttpApi register(Routes<T> routes, T controller) {
        Objects.requireNonNull(routes, "routes");
        if (!routes.controller().isInstance(controller))
            throw new IllegalArgumentException(controller + " is not a " + routes.controller().getName());
        for (Route route : routes.routes(controller)) router.add(route);
        return this;
    }

    /**
     * Starts answering.
     *
     * @throws java.io.UncheckedIOException if the address cannot be bound
     */
    public HttpApi start() {
        try {
            server = HttpServer.create(address, 0);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException("could not listen on " + address, e);
        }
        threads = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(threads);
        server.createContext("/", this::exchange);
        server.start();
        address = server.getAddress();
        return this;
    }

    /** The port it answers on; with {@link #local()}, the free one it was given. */
    public int port() {
        return address.getPort();
    }

    /** Where the API answers, such as {@code http://127.0.0.1:52144/api}. */
    public URI uri() {
        return URI.create("http://" + address.getAddress().getHostAddress() + ":" + port() + PREFIX);
    }

    @Override
    public void close() {
        if (server != null) server.stop(0);
        if (threads != null) threads.close();
    }

    // ---- answering

    private void exchange(HttpExchange exchange) throws IOException {
        try (exchange) {
            String origin = exchange.getRequestHeaders().getFirst("Origin");
            if (origin != null && origins.contains(origin)) {
                var headers = exchange.getResponseHeaders();
                headers.set("Access-Control-Allow-Origin", origin);
                headers.set("Vary", "Origin");
                headers.set("Access-Control-Allow-Headers", "Authorization, Content-Type");
                headers.set("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE");
                headers.set("Access-Control-Max-Age", "600");
            }
            if (exchange.getRequestMethod().equals("OPTIONS")) {
                send(exchange, 204, "text/plain", "");
                return;
            }
            if (origin != null && !origins.contains(origin)) {
                send(exchange, 403, "text/plain", "origin not allowed: " + origin);
                return;
            }
            if (!authorized(exchange.getRequestHeaders().getFirst("Authorization"))) {
                send(exchange, 401, "text/plain", "a valid bearer token is required");
                return;
            }
            URI uri = exchange.getRequestURI();
            String raw = uri.getRawPath();
            if (!raw.equals(PREFIX) && !raw.startsWith(PREFIX + "/")) {
                send(exchange, 404, "text/plain", "not an API path: " + raw);
                return;
            }
            String path = raw.length() == PREFIX.length() ? "/" : raw.substring(PREFIX.length());
            HttpMethod method;
            try {
                method = HttpMethod.valueOf(exchange.getRequestMethod());
            } catch (IllegalArgumentException e) {
                send(exchange, 405, "text/plain", "not a route method: " + exchange.getRequestMethod());
                return;
            }
            switch (router.match(method, path)) {
                case Router.Match.NotFound notFound -> send(exchange, 404, "text/plain", "no route " + method + " " + path);
                case Router.Match.NotAllowed notAllowed -> send(exchange, 405, "text/plain",
                        method + " " + path + " is not allowed; it answers " + notAllowed.allowed());
                case Router.Match.Found found -> {
                    RouteRequest request = new RouteRequest(null, method, path, found.pathParams(),
                            Router.query(uri.getRawQuery()), body(exchange.getRequestBody()));
                    answer(exchange, request, found);
                }
            }
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "an API request failed", e);
        }
    }

    private void answer(HttpExchange exchange, RouteRequest request, Router.Match.Found found) throws IOException {
        Object value;
        try {
            value = found.handler().handle(request);
            if (value instanceof CompletionStage<?> stage) value = stage.toCompletableFuture().join();
        } catch (CompletionException e) {
            failure(exchange, request, e.getCause() != null ? e.getCause() : e);
            return;
        } catch (Exception e) {
            failure(exchange, request, e);
            return;
        }
        if (value == null) {
            send(exchange, 204, "application/json", "");
            return;
        }
        String json;
        try {
            json = Json.write(value);
        } catch (IllegalArgumentException notJson) {
            failure(exchange, request, new IllegalStateException(request.method() + " " + request.path()
                    + " answered something that is not JSON: " + notJson.getMessage()));
            return;
        }
        send(exchange, 200, "application/json", json);
    }

    private void failure(HttpExchange exchange, RouteRequest request, Throwable error) throws IOException {
        int status = switch (error) {
            case HttpException http -> http.status();
            case IllegalArgumentException bad -> 400;
            default -> 500;
        };
        if (status >= 500) LOG.log(System.Logger.Level.WARNING, request.method() + " " + request.path() + " failed", error);
        send(exchange, status, "text/plain", error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName());
    }

    private boolean authorized(String header) {
        if (token == null) return true;
        if (header == null || !header.startsWith("Bearer ")) return false;
        return MessageDigest.isEqual(header.substring(7).getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8));
    }

    private static String body(InputStream in) throws IOException {
        byte[] bytes = in.readNBytes(MAX_BODY + 1);
        if (bytes.length > MAX_BODY) throw new HttpException(413, "the body is over 64 MB");
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void send(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        boolean empty = status == 204 || bytes.length == 0;
        exchange.sendResponseHeaders(status, empty ? -1 : bytes.length);
        if (!empty) exchange.getResponseBody().write(bytes);
    }
}
