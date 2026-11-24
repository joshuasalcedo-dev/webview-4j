package io.joshuasalcedo.webview4j.devtools;

import static io.joshuasalcedo.webview4j.ui.Ui.*;

import io.joshuasalcedo.webview4j.ui.components.*;

import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.Json;
import io.joshuasalcedo.webview4j.ui.Component;
import io.joshuasalcedo.webview4j.ui.Screen;
import io.joshuasalcedo.webview4j.ui.Ui;
import io.joshuasalcedo.webview4j.ui.UiWindow;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * A window to look at and try an HTTP API from its OpenAPI description: every endpoint with a form filled in with
 * example values, sent for real; every endpoint at once (a smoke test); a mock server that answers from the
 * description; and the project's JUnit tests, run from a button. Built with webview-4j's Java UI.
 * <pre>{@code
 * // Spring Boot with springdoc: every controller is in /v3/api-docs
 * ApiExplorer.of("http://localhost:8080/v3/api-docs").open(app);
 * // a server elsewhere, with a token
 * ApiExplorer.of("https://api.example.com/v3/api-docs").header("Authorization", "Bearer " + token).open(app);
 * }</pre>
 *
 * <p>Requests are sent from Java, not from the page: no CORS, and any server it can reach (local or not).
 */
public final class ApiExplorer implements Screen {

    private enum Mode { ENDPOINT, ALL, TESTS }

    private record Result(int status, long millis, String body, String error) {
        boolean ok() {
            return error == null && status >= 200 && status < 400;
        }
    }

    private final String specUrl;
    private final Map<String, String> headers = new LinkedHashMap<>();
    private Path project = Path.of("").toAbsolutePath();
    private String title = "API explorer";
    private boolean headless;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL).build();

    private volatile UiWindow window;
    private OpenApi api;
    private String loadError;
    private Mode mode = Mode.ENDPOINT;
    private int selected;
    private boolean mock;
    private MockServer mockServer;
    private String serverUrl;
    /** What the user typed for each endpoint: parameter values by name, and "body". */
    private final Map<String, Map<String, String>> inputs = new HashMap<>();
    private final Map<String, Result> results = new HashMap<>();
    private final Map<String, Boolean> sending = new HashMap<>();
    private boolean runningAll;
    private TestRunner tests;

    private ApiExplorer(String specUrl) {
        this.specUrl = Objects.requireNonNull(specUrl, "specUrl");
    }

    /** An explorer of the API described at {@code specUrl}: an OpenAPI 3 description in JSON. */
    public static ApiExplorer of(String specUrl) {
        return new ApiExplorer(specUrl);
    }

    /** Sent with every request: {@code header("Authorization", "Bearer ...")} for a protected API. */
    public ApiExplorer header(String name, String value) {
        headers.put(Objects.requireNonNull(name, "name"), Objects.requireNonNull(value, "value"));
        return this;
    }

    /** Where the API is, instead of what its description says ({@code servers}). */
    public ApiExplorer server(String url) {
        this.serverUrl = Objects.requireNonNull(url, "url");
        return this;
    }

    /** The project whose tests "Run tests" runs (a folder with a Gradle wrapper or a pom.xml); the working folder by default. */
    public ApiExplorer project(Path project) {
        this.project = Objects.requireNonNull(project, "project").toAbsolutePath();
        return this;
    }

    /** The window's title: "API explorer" by default. */
    public ApiExplorer title(String title) {
        this.title = Objects.requireNonNull(title, "title");
        return this;
    }

    /** Keeps the window hidden: for automation and tests. */
    public ApiExplorer headless(boolean headless) {
        this.headless = headless;
        return this;
    }

    /** Opens the explorer in a window of its own. */
    public CompletableFuture<UiWindow> open(App app) {
        load();
        return Ui.open(app, this, o -> o.title(title).size(1280, 820).minSize(800, 520).decorations(false).headless(headless))
                .thenApply(w -> {
                    window = w;
                    w.view().subscribe(io.joshuasalcedo.webview4j.Closed.class, closed -> stopAll());
                    return w;
                });
    }

    private void stopAll() {
        if (mockServer != null) mockServer.close();
        if (tests != null) tests.stop();
    }

    // ---- loading and sending ----

    private void load() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(specUrl)).timeout(Duration.ofSeconds(20)).GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) throw new IllegalStateException(specUrl + " answered " + response.statusCode());
            api = OpenApi.parse(response.body(), URI.create(specUrl));
            loadError = null;
        } catch (Exception e) {
            api = null;
            loadError = "Could not read the API description at " + specUrl + ": " + e.getMessage();
        }
    }

    private URI base() {
        if (mock) {
            if (mockServer == null) mockServer = MockServer.start(api);
            return mockServer.url();
        }
        return serverUrl != null ? URI.create(serverUrl.endsWith("/") ? serverUrl : serverUrl + "/") : api.server;
    }

    private Map<String, String> inputs(OpenApi.Operation op) {
        return inputs.computeIfAbsent(op.label(), label -> {
            Map<String, String> values = new LinkedHashMap<>();
            for (OpenApi.Parameter p : op.parameters()) {
                Object example = Examples.of(api, p.schema(), p.name());
                values.put(p.name(), example == null ? "" : String.valueOf(example));
            }
            if (op.requestSchema() != null) values.put("body", pretty(Json.write(Examples.of(api, op.requestSchema(), null))));
            return values;
        });
    }

    private Result send(OpenApi.Operation op) {
        Map<String, String> values = inputs(op);
        try {
            String path = op.path();
            StringBuilder query = new StringBuilder();
            HttpRequest.Builder request = HttpRequest.newBuilder().timeout(Duration.ofSeconds(30));
            for (OpenApi.Parameter p : op.parameters()) {
                String value = values.getOrDefault(p.name(), "");
                if (value.isEmpty() && !p.required()) continue;
                switch (p.in()) {
                    case "path" -> path = path.replace("{" + p.name() + "}", URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"));
                    case "header" -> request.header(p.name(), value);
                    default -> query.append(query.isEmpty() ? "?" : "&").append(URLEncoder.encode(p.name(), StandardCharsets.UTF_8))
                            .append('=').append(URLEncoder.encode(value, StandardCharsets.UTF_8));
                }
            }
            String basePath = base().toString().replaceAll("/+$", "");
            request.uri(URI.create(basePath + path + query));
            headers.forEach(request::header);
            String body = values.get("body");
            if (op.requestSchema() != null && body != null) {
                request.header("Content-Type", "application/json").method(op.method(), HttpRequest.BodyPublishers.ofString(body));
            } else {
                request.method(op.method(), HttpRequest.BodyPublishers.noBody());
            }
            long start = System.nanoTime();
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            long millis = (System.nanoTime() - start) / 1_000_000;
            return new Result(response.statusCode(), millis, pretty(response.body()), null);
        } catch (Exception e) {
            return new Result(0, 0, "", e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    /** Runs {@code work} off the screen's thread, then shows the screen again. */
    private void later(Runnable work) {
        Thread.ofVirtual().start(() -> {
            work.run();
            UiWindow w = window;
            if (w != null) w.refresh();
        });
    }

    private void sendLater(OpenApi.Operation op) {
        sending.put(op.label(), true);
        later(() -> {
            Result result = send(op);
            synchronized (this) {
                results.put(op.label(), result);
                sending.remove(op.label());
            }
        });
    }

    private void runAll() {
        mode = Mode.ALL;
        runningAll = true;
        List<OpenApi.Operation> operations = api.operations;
        later(() -> {
            for (OpenApi.Operation op : operations) {
                Result result = send(op);
                synchronized (this) {
                    results.put(op.label(), result);
                }
                UiWindow w = window;
                if (w != null) w.refresh();
            }
            synchronized (this) {
                runningAll = false;
            }
        });
    }

    private void runTests() {
        mode = Mode.TESTS;
        if (tests != null && tests.progress().running()) return;
        try {
            tests = TestRunner.start(project, () -> {
                UiWindow w = window;
                if (w != null) w.refresh();
            });
        } catch (RuntimeException e) {
            loadError = e.getMessage();
        }
    }

    private static String pretty(String json) {
        if (json == null || json.isBlank()) return "";
        try {
            return indent(Json.parse(json), "");
        } catch (IllegalArgumentException notJson) {
            return json;
        }
    }

    private static String indent(Object value, String pad) {
        if (value instanceof Map<?, ?> map && !map.isEmpty()) {
            List<String> lines = new ArrayList<>();
            map.forEach((k, v) -> lines.add(pad + "  " + Json.write(String.valueOf(k)) + ": " + indent(v, pad + "  ")));
            return "{\n" + String.join(",\n", lines) + "\n" + pad + "}";
        }
        if (value instanceof List<?> list && !list.isEmpty()) {
            List<String> lines = new ArrayList<>();
            for (Object v : list) lines.add(pad + "  " + indent(v, pad + "  "));
            return "[\n" + String.join(",\n", lines) + "\n" + pad + "]";
        }
        return Json.write(value);
    }

    // ---- the screen ----

    @Override
    public synchronized Component<?> render() {
        if (api == null) {
            return frame(column(
                    Ui.title("API explorer"),
                    text(loadError == null ? "Loading…" : loadError).tone(Tone.ERROR),
                    button("Try again").accent().onClick(this::load)).padding(24).gap(12))
                    .titleBar(titleBar(title));
        }
        Map<String, List<Component<?>>> byTag = new LinkedHashMap<>();
        for (int i = 0; i < api.operations.size(); i++) {
            OpenApi.Operation op = api.operations.get(i);
            int index = i;
            Result r = results.get(op.label());
            SidebarItem item = sidebarItem(op.label()).key(op.label())
                    .selected(mode == Mode.ENDPOINT && selected == i)
                    .onSelect(() -> {
                        mode = Mode.ENDPOINT;
                        selected = index;
                    });
            byTag.computeIfAbsent(op.tag(), t -> new ArrayList<>()).add(item);
            if (r != null && !r.ok()) item.badge(r.status());
        }
        Sidebar sidebar = sidebar(byTag.entrySet().stream()
                .map(e -> sidebarSection(e.getKey(), e.getValue().toArray(Component<?>[]::new)))
                .toArray(Component<?>[]::new))
                .footer(sidebarItem("Every endpoint").selected(mode == Mode.ALL).onSelect(() -> mode = Mode.ALL),
                        sidebarItem("Tests").selected(mode == Mode.TESTS).onSelect(() -> mode = Mode.TESTS));

        Component<?> content = switch (mode) {
            case ENDPOINT -> endpoint(api.operations.get(Math.min(selected, api.operations.size() - 1)));
            case ALL -> everyEndpoint();
            case TESTS -> testsPage();
        };

        long failed = api.operations.stream().map(op -> results.get(op.label())).filter(r -> r != null && !r.ok()).count();
        return frame(split(sidebar, content))
                .titleBar(titleBar(title + " · " + api.title + (api.version.isEmpty() ? "" : " " + api.version)))
                .toolbar(toolbar(mode == Mode.ENDPOINT ? "Endpoint" : mode == Mode.ALL ? "Every endpoint" : "Tests",
                        spacer(),
                        segmented("Send to", ordered("server", "Server", "mock", "Mock"))
                                .value(mock ? "mock" : "server").onChange(v -> mock = v.equals("mock")),
                        toolbarButton("Run every endpoint").onClick(this::runAll),
                        toolbarButton("Run tests").onClick(this::runTests),
                        toolbarButton("Reload").onClick(this::load)))
                .statusBar(statusBar(
                        statusItem(mock ? "Mock server " + base() : String.valueOf(base())).dot(mock ? DotState.CONNECTING : DotState.ONLINE),
                        spacer(),
                        statusItem(api.operations.size() + " endpoints" + (failed > 0 ? ", " + failed + " failing" : ""))));
    }

    private Component<?> endpoint(OpenApi.Operation op) {
        Map<String, String> values = inputs(op);
        List<Component<?>> fields = new ArrayList<>();
        for (OpenApi.Parameter p : op.parameters()) {
            fields.add(textField(p.name() + " (" + p.in() + (p.required() ? ", required" : "") + ")")
                    .key(op.label() + "/" + p.name())
                    .value(values.get(p.name()))
                    .onChange(v -> values.put(p.name(), v)));
        }
        if (op.requestSchema() != null) {
            fields.add(textArea("Body (JSON)").key(op.label() + "/body").rows(10).value(values.get("body")).onChange(v -> values.put("body", v)));
        }
        Result r = results.get(op.label());
        boolean busy = sending.containsKey(op.label());
        List<Component<?>> parts = new ArrayList<>();
        parts.add(row(pill(op.method()).color(methodColor(op.method())).filled(), text(op.path()).mono().variant(TextVariant.HEADLINE)));
        if (!op.summary().isBlank()) parts.add(text(op.summary()).tone(Tone.SECONDARY));
        parts.add(surface("Request", fields.isEmpty()
                ? new Component<?>[] {text("No parameters.").tone(Tone.SECONDARY)}
                : fields.toArray(Component<?>[]::new)));
        parts.add(row(button(busy ? "Sending…" : "Send").accent().disabled(busy).onClick(() -> sendLater(op)),
                button("Example values").onClick(() -> inputs.remove(op.label()))));
        if (r != null) {
            parts.add(surface("Response",
                    row(pill(r.error != null ? "Failed" : String.valueOf(r.status)).color(r.ok() ? PillColor.GREEN : PillColor.RED),
                            text(r.error != null ? r.error : r.millis + " ms").tone(Tone.SECONDARY)),
                    code(r.body.isEmpty() ? "(no body)" : r.body, "json")));
        }
        return column(parts).padding(24).gap(16);
    }

    private Component<?> everyEndpoint() {
        List<Component<?>> items = new ArrayList<>();
        long passed = 0;
        long failed = 0;
        for (OpenApi.Operation op : api.operations) {
            Result r = results.get(op.label());
            if (r != null && r.ok()) passed++;
            if (r != null && !r.ok()) failed++;
            items.add(listItem(op.label()).key(op.label())
                    .subtitle(r == null ? "Not sent" : r.error != null ? r.error : r.status + " in " + r.millis + " ms")
                    .trailing(r == null ? "" : r.ok() ? "Passed" : "Failed")
                    .onSelect(() -> {
                        mode = Mode.ENDPOINT;
                        selected = api.operations.indexOf(op);
                    }));
        }
        return column(
                Ui.title("Every endpoint"),
                text("Sends each endpoint its example values" + (mock ? " (to the mock server)." : ".") + " Passed: a 2xx or 3xx answer.")
                        .tone(Tone.SECONDARY),
                row(button(runningAll ? "Running…" : "Run every endpoint").accent().disabled(runningAll).onClick(this::runAll),
                        pill(passed + " passed").color(PillColor.GREEN), pill(failed + " failed").color(failed > 0 ? PillColor.RED : PillColor.GREY)),
                list("Endpoints", items)).padding(24).gap(16);
    }

    private Component<?> testsPage() {
        if (tests == null) {
            return column(
                    Ui.title("Tests"),
                    text("Runs the JUnit tests of " + project + " with its own build (Gradle or Maven), and shows each as it finishes.")
                            .tone(Tone.SECONDARY),
                    loadError != null && mode == Mode.TESTS ? text(loadError).tone(Tone.ERROR) : text(""),
                    button("Run tests").accent().onClick(this::runTests)).padding(24).gap(16);
        }
        TestRunner.Progress progress = tests.progress();
        List<Component<?>> rows = new ArrayList<>();
        for (TestRunner.TestResult t : progress.results()) {
            rows.add(listItem(t.name()).key(t.className() + "#" + t.name())
                    .subtitle(t.message() != null ? t.message() : t.className())
                    .trailing(t.status() == TestRunner.Status.PASSED ? String.format("%.2f s", t.seconds()) : t.status().name().toLowerCase()));
        }
        List<String> lines = progress.output();
        String tail = String.join("\n", lines.subList(Math.max(0, lines.size() - 30), lines.size()));
        return column(
                Ui.title("Tests"),
                row(progress.running() ? spinner("Running") : pill(progress.exitCode() != null && progress.exitCode() == 0 ? "Done" : "Ended")
                                .color(progress.exitCode() != null && progress.exitCode() == 0 ? PillColor.GREEN : PillColor.RED),
                        text(progress.summary()),
                        button("Run again").disabled(progress.running()).onClick(this::runTests),
                        button("Stop").disabled(!progress.running()).onClick(tests::stop)),
                progress.running() ? progress("Running the tests", 0).indeterminate() : text(progress.command()).mono().tone(Tone.SECONDARY),
                list("Tests", rows),
                surface("Output", code(tail.isEmpty() ? "(nothing yet)" : tail, "plaintext"))).padding(24).gap(16);
    }

    private static Map<String, String> ordered(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) map.put(pairs[i], pairs[i + 1]);
        return map;
    }

    private static PillColor methodColor(String method) {
        return switch (method) {
            case "GET" -> PillColor.BLUE;
            case "POST" -> PillColor.GREEN;
            case "PUT", "PATCH" -> PillColor.ORANGE;
            case "DELETE" -> PillColor.RED;
            default -> PillColor.GREY;
        };
    }
}
