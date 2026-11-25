package io.joshuasalcedo.webview4j.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.webclient.autoconfigure.WebClientAutoConfiguration;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

/** The dev logger's sources in the starter: Logback, RestClient/RestTemplate, WebClient, and what waits for the window. */
class DevLogTest {

    private HttpServer server;
    private String base;

    @BeforeEach
    void serve() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/rates", x -> answer(x, 200, "application/json", "{\"usd\":56.1}"));
        server.createContext("/broken", x -> answer(x, 503, "application/problem+json", "{\"title\":\"down\"}"));
        server.createContext("/events", x -> answer(x, 200, "text/event-stream", "data: one\n\ndata: two\n\n"));
        server.createContext("/picture", x -> answer(x, 200, "image/png", "PNG..."));
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private static void answer(com.sun.net.httpserver.HttpExchange x, int status, String type, String body) throws IOException {
        x.getRequestBody().readAllBytes();
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        x.getResponseHeaders().add("Content-Type", type);
        x.sendResponseHeaders(status, bytes.length);
        x.getResponseBody().write(bytes);
        x.close();
    }

    private RestClient restClient(DevLogSink sink) {
        return RestClient.builder().requestInterceptor(new DevLogHttp(sink)).build();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> part(Map<String, Object> entry, String name) {
        return (Map<String, Object>) entry.get(name);
    }

    // ---- what waits for the window ----

    @Test
    void entriesWaitForTheWindowAndOnlyTheLatestAreKept() {
        DevLogSink sink = new DevLogSink(true);
        for (int i = 0; i < DevLogSink.EARLY + 10; i++) sink.add(Map.of("n", i));
        List<Map<String, Object>> waiting = sink.waiting();
        assertEquals(DevLogSink.EARLY, waiting.size());
        assertEquals(10, waiting.getFirst().get("n"), "the oldest went first");

        DevLogSink off = new DevLogSink(false);
        off.add(Map.of("n", 1));
        assertTrue(off.waiting().isEmpty(), "not wanted: nothing kept");
    }

    // ---- RestClient ----

    @Test
    void aCallIsLoggedAndItsAnswerStillRead() {
        DevLogSink sink = new DevLogSink(true);
        String answer = restClient(sink).get().uri(base + "/rates")
                .header("Accept", "application/json").header("Authorization", "Bearer secret-token")
                .retrieve().body(String.class);
        assertEquals("{\"usd\":56.1}", answer, "the app still gets the body");

        Map<String, Object> e = sink.waiting().getFirst();
        assertEquals("http", e.get("kind"));
        assertEquals("java", e.get("source"));
        assertEquals("GET", e.get("method"));
        assertEquals(base + "/rates", e.get("url"));
        assertEquals(200, e.get("status"));
        assertTrue(((Double) e.get("durationMs")) >= 0);
        assertEquals("(hidden)", part(e, "request").get("headers") instanceof Map<?, ?> h ? h.get("authorization") : null, "no secrets");
        assertEquals("application/json", ((Map<?, ?>) part(e, "request").get("headers")).get("accept"));
        assertEquals("{\"usd\":56.1}", part(e, "response").get("body"));
    }

    @Test
    void aFailedAnswerIsLoggedWithItsBody() {
        DevLogSink sink = new DevLogSink(true);
        assertThrows(Exception.class, () -> restClient(sink).get().uri(base + "/broken").retrieve().body(String.class));
        Map<String, Object> e = sink.waiting().getFirst();
        assertEquals(503, e.get("status"));
        assertEquals("{\"title\":\"down\"}", part(e, "response").get("body"));
    }

    @Test
    void aStreamIsNotReadSoItStillStreams() {
        DevLogSink sink = new DevLogSink(true);
        String streamed = restClient(sink).get().uri(base + "/events").retrieve().body(String.class);
        assertEquals("data: one\n\ndata: two\n\n", streamed);
        Map<String, Object> e = sink.waiting().getFirst();
        assertEquals(200, e.get("status"));
        assertNull(part(e, "response").get("body"), "not read");
        assertEquals("text/event-stream", ((Map<?, ?>) part(e, "response").get("headers")).get("content-type"));
    }

    @Test
    void aPictureIsNotWrittenAsText() {
        DevLogSink sink = new DevLogSink(true);
        restClient(sink).get().uri(base + "/picture").retrieve().body(byte[].class);
        assertNull(part(sink.waiting().getFirst(), "response").get("body"));
    }

    @Test
    void noAnswerIsLoggedAsAnErrorAndStillThrown() throws IOException {
        DevLogSink sink = new DevLogSink(true);
        int closed = server.getAddress().getPort();
        server.stop(0);
        assertThrows(Exception.class, () -> restClient(sink).post().uri("http://127.0.0.1:" + closed + "/pay")
                .header("Content-Type", "application/json").body("{\"amount\":10}").retrieve().body(String.class));
        Map<String, Object> e = sink.waiting().getFirst();
        assertEquals("POST", e.get("method"));
        assertTrue(e.containsKey("error"), String.valueOf(e));
        assertEquals("{\"amount\":10}", part(e, "request").get("body"));
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
    }

    // ---- WebClient ----

    @Test
    void aWebClientCallIsLogged() {
        DevLogSink sink = new DevLogSink(true);
        String answer = WebClient.builder().clientConnector(new JdkClientHttpConnector()).filter(new DevLogWebClient(sink)).build()
                .get().uri(base + "/rates").retrieve().bodyToMono(String.class).block(Duration.ofSeconds(10));
        assertEquals("{\"usd\":56.1}", answer);
        Map<String, Object> e = sink.waiting().getFirst();
        assertEquals(200, e.get("status"));
        assertEquals("application/json", ((Map<?, ?>) part(e, "response").get("headers")).get("content-type"));
    }

    // ---- Logback ----

    @Test
    void logbackLinesGoToTheLogger() {
        DevLogSink sink = new DevLogSink(true);
        assertTrue(DevLogAppender.install(sink), "Logback is the test's logging");
        assertTrue(DevLogAppender.install(sink), "a second install adds nothing");
        try {
            LoggerFactory.getLogger("com.example.Orders").warn("retrying order {}", 7);
            LoggerFactory.getLogger("com.example.Orders").error("failed", new IllegalStateException("locked"));
            LoggerFactory.getLogger(io.joshuasalcedo.webview4j.DevLogger.class).warn("about itself");
            List<Map<String, Object>> lines = sink.waiting().stream().filter(e -> "com.example.Orders".equals(e.get("target"))).toList();
            assertEquals(2, lines.size(), String.valueOf(sink.waiting()));
            assertEquals("warn", lines.get(0).get("level"));
            assertEquals("retrying order 7", lines.get(0).get("message"));
            assertEquals("failed (java.lang.IllegalStateException: locked)", lines.get(1).get("message"));
            assertTrue(sink.waiting().stream().noneMatch(e -> String.valueOf(e.get("message")).contains("about itself")));
        } finally {
            var context = (ch.qos.logback.classic.LoggerContext) LoggerFactory.getILoggerFactory();
            context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).detachAppender(DevLogAppender.NAME);
        }
    }

    // ---- the starter wires them in ----

    @Test
    void theStarterAddsThemToTheAppsClients() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DevLogConfiguration.class, RestClientAutoConfiguration.class,
                        WebClientAutoConfiguration.class))
                .withPropertyValues("webview4j.enabled=true", "webview4j.dev-logger=true")
                .run(context -> {
                    DevLogSink sink = context.getBean(DevLogSink.class);
                    assertTrue(sink.on());
                    RestClient client = context.getBean(RestClient.Builder.class).build();
                    client.get().uri(base + "/rates").retrieve().body(String.class);
                    assertTrue(sink.waiting().stream().anyMatch(e -> (base + "/rates").equals(e.get("url"))), "RestClient.Builder has it");

                    WebClient web = context.getBean(WebClient.Builder.class).clientConnector(new JdkClientHttpConnector()).build();
                    web.get().uri(base + "/broken").retrieve().toBodilessEntity().onErrorComplete().block(Duration.ofSeconds(10));
                    assertTrue(sink.waiting().stream().anyMatch(e -> (base + "/broken").equals(e.get("url"))), "WebClient.Builder has it");
                });
        // off: the customizers are there, and add nothing
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(DevLogConfiguration.class, RestClientAutoConfiguration.class))
                .withPropertyValues("webview4j.enabled=true", "webview4j.dev-logger=false")
                .run(context -> {
                    DevLogSink sink = context.getBean(DevLogSink.class);
                    context.getBean(RestClient.Builder.class).build().get().uri(base + "/rates").retrieve().body(String.class);
                    assertTrue(sink.waiting().isEmpty());
                });
    }
}
