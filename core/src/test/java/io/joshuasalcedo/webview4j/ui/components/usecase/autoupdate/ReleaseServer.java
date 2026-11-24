package io.joshuasalcedo.webview4j.ui.components.usecase.autoupdate;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/** A release server for the tests: a manifest, its signature and an installer, at real URLs. */
abstract class ReleaseServer {

    /** A release server: files by path. */
    HttpServer server;
    final Map<String, byte[]> files = new ConcurrentHashMap<>();

    @BeforeEach
    void serve() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = files.get(exchange.getRequestURI().getPath());
            if (body == null) {
                exchange.sendResponseHeaders(404, -1);
            } else {
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    URI url(String path) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
    }

    /** A release: its installer and its manifest, signed with {@code key}. */
    void release(String version, byte[] installer, String privateKey) {
        files.put("/Notes-" + version + ".msi", installer);
        UpdateManifest m = new UpdateManifest("notes", Version.of(version), "2026-12-01", "- Better", Map.of(UpdateManifest.platform(),
                new UpdateManifest.Download(url("/Notes-" + version + ".msi"), sha256(installer), installer.length)));
        byte[] json = m.toJson();
        files.put("/manifest.json", json);
        files.put("/manifest.json.sig", UpdateSignature.sign(json, privateKey).getBytes(StandardCharsets.US_ASCII));
    }

    static String sha256(byte[] b) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(b));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
