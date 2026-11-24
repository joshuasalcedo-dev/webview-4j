package io.joshuasalcedo.webview4j.ui;

import static io.joshuasalcedo.webview4j.ui.Ui.*;
import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Who may use a web host, TLS, and a screen's host (files, dialogs, notifications) in a browser tab. */
class WebHostSecurityTest {

    static final class Office implements Screen {
        @Override
        public Component<?> render() {
            String who = WebHost.account().flatMap(WebHost.Account::user).map(WebHost.User::name).orElse("nobody");
            return column(text("office of " + who), button("Sign out").onClick(() -> WebHost.account().orElseThrow().signOut()));
        }
    }

    static final WebHost.Authenticator USERS = (name, password) ->
            name.equals("ana") && password.equals("correct horse") ? Optional.of(WebHost.User.named("ana", "staff"))
                    : name.equals("ben") && password.equals("battery") ? Optional.of(WebHost.User.named("ben")) : Optional.empty();

    static String json(Object o) {
        return io.joshuasalcedo.webview4j.Json.write(o);
    }

    /** Types into the sign-in form and presses Sign in: the new tree. */
    static Map<String, Object> signIn(WebHostTest.Tab tab, String name, String password) throws Exception {
        Object tree = tab.invoke("webview4j.ui.ready", null).get("result");
        tab.invoke("webview4j.ui.event", Map.of("handler", field(tree, "Name"), "value", name));
        tab.invoke("webview4j.ui.event", Map.of("handler", field(tree, "Password"), "value", password));
        return tab.invoke("webview4j.ui.event", Map.of("handler", WebHostTest.handlerOf(tree, "Sign in")));
    }

    @SuppressWarnings("unchecked")
    static String field(Object tree, String label) {
        if (tree instanceof Map<?, ?> node) {
            Map<String, Object> p = (Map<String, Object>) node.get("p");
            if (p != null && label.equals(p.get("label")) && p.get("onChange") instanceof String h) return h;
            if (node.get("c") instanceof List<?> children) for (Object c : children) {
                String h = field(c, label);
                if (h != null) return h;
            }
        }
        return null;
    }

    /** The cookie the host sets when the browser follows a sign-in (or sign-out) link. */
    static String follow(URI host, String url, String cookie) throws Exception {
        HttpResponse<Void> r = HttpClient.newHttpClient().send(HttpRequest.newBuilder(host.resolve(url)).header("Cookie", cookie).build(),
                HttpResponse.BodyHandlers.discarding());
        assertEquals(303, r.statusCode());
        return r.headers().firstValue("Set-Cookie").orElseThrow().split(";")[0];
    }

    @Test
    void aSignInScreenFirstThenANewSessionIdAndTheApp() throws Exception {
        try (WebHost host = WebHost.of(Office::new).title("Office").signIn(USERS).allow(u -> u.has("staff")).start()) {
            String before = WebHostTest.cookie(host.url());
            try (WebHostTest.Tab tab = new WebHostTest.Tab(host.url(), before, new ConcurrentHashMap<>())) {
                String first = json(tab.invoke("webview4j.ui.ready", null).get("result"));
                assertTrue(first.contains("Sign in to go on.") && !first.contains("office of"), "the sign-in screen, not the app");

                assertTrue(json(signIn(tab, "ana", "wrong").get("result")).contains("do not match"));

                signIn(tab, "ana", "correct horse");
                Map<String, Object> go = tab.pushed.poll(5, TimeUnit.SECONDS);
                assertNotNull(go);
                assertEquals("webview4j.web.navigate", go.get("topic"));
                String link = String.valueOf(((Map<?, ?>) go.get("data")).get("url"));
                String after = follow(host.url(), link, before);
                assertNotEquals(before, after, "signed in: a new session id");
                assertEquals(303, HttpClient.newHttpClient().send(HttpRequest.newBuilder(host.url().resolve(link)).build(),
                        HttpResponse.BodyHandlers.discarding()).statusCode());

                try (WebHostTest.Tab signedIn = new WebHostTest.Tab(host.url(), after, new ConcurrentHashMap<>())) {
                    Object tree = signedIn.invoke("webview4j.ui.ready", null).get("result");
                    assertTrue(json(tree).contains("office of ana"), json(tree));
                    // signing out: every tab goes back to the start, the session is gone
                    signedIn.invoke("webview4j.ui.event", Map.of("handler", WebHostTest.handlerOf(tree, "Sign out")));
                    Map<String, Object> out = signedIn.pushed.poll(5, TimeUnit.SECONDS);
                    assertTrue(String.valueOf(out).contains("/webview4j/signed-out"), String.valueOf(out));
                }
                // the old id is no use: a session of its own, not signed in
                try (WebHostTest.Tab old = new WebHostTest.Tab(host.url(), before, new ConcurrentHashMap<>())) {
                    assertTrue(json(old.invoke("webview4j.ui.ready", null).get("result")).contains("Sign in to go on."));
                }
            }
        }
    }

    @Test
    void triesAreLimitedAndOnlyThoseAllowedGetIn() throws Exception {
        try (WebHost host = WebHost.of(Office::new).signIn(USERS).allow(u -> u.has("staff")).start()) {
            String cookie = WebHostTest.cookie(host.url());
            try (WebHostTest.Tab tab = new WebHostTest.Tab(host.url(), cookie, new ConcurrentHashMap<>())) {
                for (int i = 0; i < 5; i++) signIn(tab, "ben", "nope");
                assertTrue(json(signIn(tab, "ben", "battery").get("result")).contains("Too many tries"), "even the right one, for a while");
            }
            String other = WebHostTest.cookie(host.url());
            try (WebHostTest.Tab tab = new WebHostTest.Tab(host.url(), other, new ConcurrentHashMap<>())) {
                signIn(tab, "ana", "nope");   // a different name: not limited by ben's tries... but the same address is
            }
        }
        // ben (not staff), from an address of its own: signed in, but told it is not for him
        try (WebHost host = WebHost.of(Office::new).signIn(USERS).allow(u -> u.has("staff")).start()) {
            String cookie = WebHostTest.cookie(host.url());
            try (WebHostTest.Tab tab = new WebHostTest.Tab(host.url(), cookie, new ConcurrentHashMap<>())) {
                signIn(tab, "ben", "battery");
                String link = String.valueOf(((Map<?, ?>) tab.pushed.poll(5, TimeUnit.SECONDS).get("data")).get("url"));
                String after = follow(host.url(), link, cookie);
                try (WebHostTest.Tab in = new WebHostTest.Tab(host.url(), after, new ConcurrentHashMap<>())) {
                    String tree = json(in.invoke("webview4j.ui.ready", null).get("result"));
                    assertTrue(tree.contains("This app is not for you") && tree.contains("signed in as ben"), tree);
                }
            }
        }
    }

    @Test
    void aProxyOnTheSameMachineSaysWhoItIs() throws Exception {
        try (WebHost host = WebHost.of(Office::new).trustProxyUser("X-Forwarded-User", "X-Forwarded-Groups").allow(u -> u.has("staff")).start()) {
            String cookie = WebHostTest.cookie(host.url());
            var ws = HttpClient.newHttpClient().newWebSocketBuilder().header("Cookie", cookie).header("Origin", "http://" + host.url().getRawAuthority())
                    .header("X-Forwarded-User", "grace").header("X-Forwarded-Groups", "staff, admins");
            WebHostTest.Tab tab = new WebHostTest.Tab(host.url(), cookie, new ConcurrentHashMap<>(), ws);
            try (tab) {
                assertTrue(json(tab.invoke("webview4j.ui.ready", null).get("result")).contains("office of grace"));
            }
            // without the header: nobody, and nothing to sign in with here
            try (WebHostTest.Tab none = new WebHostTest.Tab(host.url(), WebHostTest.cookie(host.url()), new ConcurrentHashMap<>())) {
                assertTrue(json(none.invoke("webview4j.ui.ready", null).get("result")).contains("Sign in first"));
            }
        }
    }

    @Test
    void itServesHttpsItself(@TempDir Path dir) throws Exception {
        Path store = dir.resolve("server.p12");
        String keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
        Process p = new ProcessBuilder(keytool, "-genkeypair", "-alias", "web", "-keyalg", "EC", "-groupname", "secp256r1", "-dname", "CN=127.0.0.1",
                "-ext", "SAN=ip:127.0.0.1", "-validity", "2", "-storetype", "PKCS12", "-keystore", store.toString(), "-storepass", "secret12", "-keypass", "secret12")
                .redirectErrorStream(true).start();
        assertEquals(0, p.waitFor(), new String(p.getInputStream().readAllBytes()));
        assertTrue(Files.exists(store));

        try (WebHost host = WebHost.of(Office::new).tls(store, "secret12".toCharArray()).start()) {
            assertEquals("https", host.url().getScheme());
            SSLContext trustAll = SSLContext.getInstance("TLS");
            trustAll.init(null, new TrustManager[] {new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] c, String a) {}
                public void checkServerTrusted(X509Certificate[] c, String a) {}
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            }}, null);
            HttpResponse<String> page = HttpClient.newBuilder().sslContext(trustAll).build()
                    .send(HttpRequest.newBuilder(host.url()).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, page.statusCode());
            assertTrue(page.headers().firstValue("Strict-Transport-Security").isPresent(), "HSTS");
            assertTrue(page.headers().firstValue("Set-Cookie").orElseThrow().contains("Secure"), "the cookie only over TLS");
        }
    }

    // ---- the screen's host in a browser tab ----

    static final class Importer implements Screen {
        String said = "";

        @Override
        public Component<?> render() {
            Host host = Host.current();
            return column(text("on the web " + host.onWeb() + ", folders " + host.supports(Host.Feature.FOLDERS) + " " + said),
                    button("Ask").onClick(() -> said = "answered " + host.confirm("Delete?", "It cannot be undone.").join()),
                    button("Open").onClick(() -> said = "opened " + host.openFile("Import", "csv").join().map(f -> f.name() + "=" + f.text()).orElse("none")),
                    button("Save").onClick(() -> said = "saved " + host.saveFile("report.csv", "a,b".getBytes()).join()),
                    button("Folder").onClick(() -> said = "folder " + host.pickFolder("Where?").handle((f, e) -> e == null ? "?"
                            : ((UiException) (e instanceof java.util.concurrent.CompletionException ? e.getCause() : e)).userMessage()).join()));
        }
    }

    @Test
    void aTabsHostAsksTheBrowser() throws Exception {
        try (WebHost host = WebHost.of(Importer::new).start()) {
            String cookie = WebHostTest.cookie(host.url());
            Map<String, Object> answers = new ConcurrentHashMap<>();
            try (WebHostTest.Tab tab = new WebHostTest.Tab(host.url(), cookie, new ConcurrentHashMap<>())) {
                tab.hostAnswers = m -> switch (String.valueOf(m.get("host"))) {
                    case "confirm" -> Map.of("answer", true);
                    case "openFiles" -> Map.of("files", List.of(Map.of("name", "people.csv", "size", 3, "data", java.util.Base64.getEncoder().encodeToString("x,y".getBytes()))));
                    case "download" -> {
                        answers.put("download", m.get("name") + "=" + new String(java.util.Base64.getDecoder().decode(String.valueOf(m.get("data")))));
                        yield Map.of("ok", true);
                    }
                    default -> Map.of();
                };
                Object tree = tab.invoke("webview4j.ui.ready", null).get("result");
                assertTrue(json(tree).contains("on the web true, folders false"), json(tree));
                assertTrue(json(tab.invoke("webview4j.ui.event", Map.of("handler", WebHostTest.handlerOf(tree, "Ask"))).get("result")).contains("answered true"));
                assertTrue(json(tab.invoke("webview4j.ui.event", Map.of("handler", WebHostTest.handlerOf(tree, "Open"))).get("result")).contains("opened people.csv=x,y"));
                assertTrue(json(tab.invoke("webview4j.ui.event", Map.of("handler", WebHostTest.handlerOf(tree, "Save"))).get("result")).contains("saved true"));
                assertEquals("report.csv=a,b", answers.get("download"));
                assertTrue(json(tab.invoke("webview4j.ui.event", Map.of("handler", WebHostTest.handlerOf(tree, "Folder"))).get("result")).contains("folder A web page cannot open a folder"));
            }
        }
    }
}
