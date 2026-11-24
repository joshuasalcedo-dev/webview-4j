package io.joshuasalcedo.webview4j.spring;

import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.Closed;
import io.joshuasalcedo.webview4j.View;
import io.joshuasalcedo.webview4j.WindowOptions;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

/**
 * The app's window, opened once the app is ready (its server listening). With nothing set, it finds out what the
 * app has: a page at {@code /} opens in the window; an OpenAPI description opens in the API explorer, beside the
 * page, or as the app's window when there is no page. Inject it to reach the window's {@link View}.
 */
public class WebView4jWindow implements ApplicationListener<ApplicationReadyEvent> {

    private static final System.Logger LOG = System.getLogger(WebView4jWindow.class.getName());

    /** Where frameworks put an OpenAPI description: springdoc, the common file, Quarkus, others. */
    static final List<String> API_DOCS = List.of("/v3/api-docs", "/openapi.json", "/q/openapi?format=json", "/openapi");

    private final App app;
    private final WebView4jProperties properties;
    private final CompletableFuture<View> view = new CompletableFuture<>();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    public WebView4jWindow(App app, WebView4jProperties properties) {
        this.app = app;
        this.properties = properties;
    }

    /** The app's window (its page, or the API explorer when it has no page), once it is open. */
    public CompletableFuture<View> view() {
        return view;
    }

    /** The app's own server: {@code http://localhost:<port><context path>}. */
    public String server(Environment environment) {
        String port = environment.getProperty("local.server.port", environment.getProperty("server.port", "8080"));
        String context = environment.getProperty("server.servlet.context-path", "").replaceAll("/+$", "");
        return "http://localhost:" + port + context;
    }

    /** A client of a server: what the server says changed is fetched again (its ServerState and pages' queries). */
    private void listenToInvalidations(Environment environment) {
        String at = properties.getInvalidations();
        if ("none".equalsIgnoreCase(at)) return;
        if (at == null || at.isBlank()) {
            String server = properties.getClientUrl() != null ? properties.getClientUrl() : environment.getProperty("webview4j.client-url");
            if (server == null || server.isBlank()) return;
            at = server.replaceAll("/+$", "") + "/webview4j/invalidations";
        }
        var session = io.joshuasalcedo.webview4j.ui.UiWindow.session(app);
        var listening = io.joshuasalcedo.webview4j.ui.state.Invalidations.listen(session, java.net.URI.create(at));
        app.whenStopped().thenRun(listening::close);
    }

    /** The page the window opens: webview4j.url, or webview4j.path (or {@code /}) on the app's own server. */
    public String address(Environment environment) {
        if (properties.getUrl() != null && !properties.getUrl().isBlank()) return properties.getUrl();
        String path = properties.getPath() == null || properties.getPath().isBlank() ? "/" : properties.getPath();
        return server(environment) + (path.startsWith("/") ? path : "/" + path);
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        ConfigurableApplicationContext context = event.getApplicationContext();
        Environment environment = context.getEnvironment();
        String title = properties.getTitle() != null ? properties.getTitle()
                : environment.getProperty("spring.application.name", "App");
        boolean ownServer = properties.getUrl() == null || properties.getUrl().isBlank();

        if (ownServer && environment.getProperty("local.server.port") == null) {
            // No web server of its own: a desktop client (@WebViewApplication), made from its screens and clients.
            // The main window: the app's pages; in development, the developer window beside it.
            boolean development = development(environment, context);
            var router = io.joshuasalcedo.webview4j.spring.client.PagesWindow.router(context, title, development);
            watch(io.joshuasalcedo.webview4j.spring.client.PagesWindow.open(app, router,
                    properties.getWidth(), properties.getHeight(), properties.isHeadless()), context);
            listenToInvalidations(environment);
            // The developer window needs Spring's HTTP service clients (spring-web); an app of pages only has none.
            if (development && io.joshuasalcedo.webview4j.spring.client.ClientWindow.available()) {
                io.joshuasalcedo.webview4j.spring.client.ClientWindow.open(app, context, title + ": developer",
                        properties.getWidth(), properties.getHeight(), properties.isHeadless());
            }
            return;
        }

        // What the app has: a page to show, and a description of its API.
        boolean page = !ownServer || properties.getPath() != null || isPage(server(environment) + "/");
        boolean wantExplorer = !Boolean.FALSE.equals(properties.getApiExplorer()) && ApiExplorerLauncher.available();
        Optional<String> docs = wantExplorer && ownServer ? apiDocs(server(environment)) : Optional.empty();
        if (Boolean.TRUE.equals(properties.getApiExplorer()) && docs.isEmpty()) {
            LOG.log(System.Logger.Level.WARNING, "webview4j.api-explorer: no OpenAPI description at " + server(environment)
                    + " (tried " + (properties.getApiDocs() != null ? properties.getApiDocs() : String.join(", ", API_DOCS)) + ")");
        }

        CompletableFuture<View> main;
        if (page || docs.isEmpty()) {
            main = app.open(WindowOptions.url(address(environment)).title(title)
                    .size(properties.getWidth(), properties.getHeight())
                    .decorations(properties.isDecorations())
                    .headless(properties.isHeadless())
                    .devtools(properties.isDevtools()));
            docs.ifPresent(url -> ApiExplorerLauncher.open(app, url, title + ": API explorer", properties.isHeadless()));
        } else {
            // An API with no page: the API explorer is the app's window.
            main = ApiExplorerLauncher.open(app, docs.get(), title, properties.isHeadless());
        }
        watch(main, context);
    }

    /** The app's window: kept for view(), and closing it stops the app (webview4j.quit-on-close). */
    private void watch(CompletableFuture<View> main, ConfigurableApplicationContext context) {
        main.whenComplete((opened, error) -> {
            if (error != null) {
                LOG.log(System.Logger.Level.ERROR, "the app's window could not open", error);
                view.completeExceptionally(error);
                return;
            }
            view.complete(opened);
            if (properties.isQuitOnClose()) {
                // Closing the window stops the app: Spring shuts down cleanly, then the process ends.
                opened.subscribe(Closed.class, closed -> Thread.ofPlatform().name("webview4j-quit").start(() ->
                        System.exit(SpringApplication.exit(context))));
            }
        });
    }

    /**
     * Whether the app runs in development: {@code webview4j.dev} when set; otherwise whether it runs from compiled
     * classes (an IDE, {@code spring-boot:run}, {@code bootRun}) rather than a packaged jar.
     */
    static boolean development(Environment environment, ConfigurableApplicationContext context) {
        String dev = environment.getProperty("webview4j.dev");
        if (dev != null) return Boolean.parseBoolean(dev);
        if (Boolean.getBoolean("webview4j.dev") || "true".equalsIgnoreCase(System.getenv("WEBVIEW4J_DEV"))) return true;
        for (String name : context.getBeanNamesForAnnotation(org.springframework.boot.autoconfigure.SpringBootApplication.class)) {
            Class<?> type = org.springframework.aop.support.AopUtils.getTargetClass(context.getBean(name));
            var source = type.getProtectionDomain().getCodeSource();
            if (source != null && source.getLocation() != null) return !source.getLocation().getPath().endsWith(".jar")
                    && !source.getLocation().getPath().contains(".jar!");
        }
        return false;
    }

    /** Whether {@code url} answers with a page (HTML), as a browser would ask for it. */
    boolean isPage(String url) {
        try {
            HttpResponse<Void> response = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5))
                    .header("Accept", "text/html").GET().build(), HttpResponse.BodyHandlers.discarding());
            return response.statusCode() < 300
                    && response.headers().firstValue("Content-Type").orElse("").contains("text/html");
        } catch (Exception e) {
            return false;
        }
    }

    /** The app's OpenAPI description: webview4j.api-docs, or the first of the usual places that has one. */
    Optional<String> apiDocs(String server) {
        List<String> places = properties.getApiDocs() != null ? List.of(properties.getApiDocs()) : API_DOCS;
        for (String place : places) {
            String url = server + (place.startsWith("/") ? place : "/" + place);
            try {
                HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10))
                        .header("Accept", "application/json").GET().build(), HttpResponse.BodyHandlers.ofString());
                String body = response.body().stripLeading();
                if (response.statusCode() < 300 && body.startsWith("{") && body.contains("\"openapi\"")) return Optional.of(url);
            } catch (Exception e) {
                // not there: the next place
            }
        }
        return Optional.empty();
    }
}
