package io.joshuasalcedo.webview4j.ui.components.usecase.authentication;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.Json;
import io.joshuasalcedo.webview4j.SecretStore;
import io.joshuasalcedo.webview4j.ui.UiException;
import io.joshuasalcedo.webview4j.ui.UiWindow;
import io.joshuasalcedo.webview4j.ui.state.StateStorage;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** Signing in against a real OpenID Connect provider (in the test), the way a desktop app must: PKCE, a loopback redirect. */
@EnabledOnOs(OS.WINDOWS)
class AuthTest {

    /** What the provider does wrong, when a test asks. */
    volatile String tamper = "";
    volatile int expiresIn = 3600;
    final List<String> revoked = new CopyOnWriteArrayList<>();
    final Map<String, String[]> codes = new ConcurrentHashMap<>();   // code -> {challenge, nonce, client}
    final Map<String, Integer> issued = new ConcurrentHashMap<>();
    HttpServer provider;
    KeyPair key;
    String issuer;

    @BeforeEach
    void provider() throws Exception {
        key = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        issuer = "http://127.0.0.1:" + provider.getAddress().getPort() + "/realms/office";
        provider.createContext("/realms/office/.well-known/openid-configuration", x -> json(x, Map.of("issuer", issuer,
                "authorization_endpoint", issuer + "/auth", "token_endpoint", issuer + "/token", "jwks_uri", issuer + "/certs",
                "revocation_endpoint", issuer + "/revoke")));
        provider.createContext("/realms/office/certs", x -> {
            RSAPublicKey pub = (RSAPublicKey) key.getPublic();
            json(x, Map.of("keys", List.of(Map.of("kty", "RSA", "kid", "k1", "alg", "RS256", "n", b64(unsigned(pub.getModulus())),
                    "e", b64(unsigned(pub.getPublicExponent()))))));
        });
        provider.createContext("/realms/office/auth", x -> {
            Map<String, String> q = Oidc.query(x.getRequestURI().getRawQuery());
            assertEquals("S256", q.get("code_challenge_method"), "PKCE");
            assertEquals("code", q.get("response_type"));
            String code = "code-" + System.nanoTime();
            codes.put(code, new String[] {q.get("code_challenge"), q.get("nonce"), q.get("client_id")});
            String state = tamper.equals("state") ? "someone-else" : q.get("state");
            x.getResponseHeaders().add("Location", q.get("redirect_uri") + "?code=" + code + "&state=" + state);
            x.sendResponseHeaders(302, -1);
            x.close();
        });
        provider.createContext("/realms/office/token", x -> {
            Map<String, String> f = Oidc.query(new String(x.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String nonce = null;
            if ("authorization_code".equals(f.get("grant_type"))) {
                String[] c = codes.remove(f.get("code"));
                if (c == null || !c[0].equals(b64(sha256(f.get("code_verifier").getBytes(StandardCharsets.US_ASCII))))) {
                    x.sendResponseHeaders(400, -1);
                    x.close();
                    return;
                }
                nonce = c[1];
            } else if (!"refresh_token".equals(f.get("grant_type")) || !f.get("refresh_token").startsWith("refresh-")) {
                json(x, 400, Map.of("error", "invalid_grant", "error_description", "the refresh token is no longer good"));
                return;
            }
            int n = issued.merge("tokens", 1, Integer::sum);
            json(x, Map.of("access_token", "access-" + n, "refresh_token", "refresh-" + n, "expires_in", expiresIn, "token_type", "Bearer",
                    "id_token", idToken(nonce, f.get("client_id"))));
        });
        provider.createContext("/realms/office/revoke", x -> {
            revoked.add(Oidc.query(new String(x.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)).get("token"));
            x.sendResponseHeaders(200, -1);
            x.close();
        });
        provider.start();
    }

    @AfterEach
    void stop() {
        provider.stop(0);
        SecretStore.of("webview4j-auth-test").delete("keycloak");
    }

    /** A browser: it follows the provider's page to the app's loopback listener. */
    static Consumer<URI> browser() {
        HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
        return uri -> Thread.ofVirtual().start(() -> {
            try {
                http.send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.discarding());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    OidcProvider keycloak() {
        return OidcProvider.of("keycloak", "Sign in with Keycloak", URI.create(issuer), "office-desktop");
    }

    String idToken(String nonce, String client) {
        long now = Instant.now().getEpochSecond();
        Map<String, Object> claims = new java.util.LinkedHashMap<>();
        claims.put("iss", issuer);
        claims.put("aud", tamper.equals("audience") ? "another-app" : client);
        claims.put("sub", "u-1");
        claims.put("name", "Ana Reyes");
        claims.put("email", "ana@example.com");
        claims.put("realm_access", Map.of("roles", List.of("staff", "admin")));
        claims.put("iat", now);
        claims.put("exp", now + 300);
        if (nonce != null) claims.put("nonce", tamper.equals("nonce") ? "not-yours" : nonce);
        String head = b64(Json.write(Map.of("alg", "RS256", "kid", "k1", "typ", "JWT")).getBytes(StandardCharsets.UTF_8));
        String body = b64(Json.write(claims).getBytes(StandardCharsets.UTF_8));
        try {
            Signature s = Signature.getInstance("SHA256withRSA");
            s.initSign(key.getPrivate());
            s.update((head + "." + body).getBytes(StandardCharsets.US_ASCII));
            byte[] sig = s.sign();
            if (tamper.equals("signature")) sig[0] ^= 1;
            return head + "." + body + "." + b64(sig);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void signingInThroughTheBrowserGivesTheAccountAndATokenThatIsRenewed() throws Exception {
        try (App app = App.start()) {
            UiWindow.session(app).storage(StateStorage.memory());
            expiresIn = 30;   // runs out within a minute: renewed before it is handed out
            Auth auth = Auth.configure(app, Auth.Options.of("webview4j-auth-test").provider(keycloak()).browser(browser()));
            Auth.Account a = auth.signIn("keycloak").get(20, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals("Ana Reyes", a.name());
            assertEquals("ana@example.com", a.email());
            assertTrue(a.has("staff") && a.has("admin"), "Keycloak's realm roles: " + a.roles());
            String token = UiWindow.session(app).call(() -> Auth.bearer().orElseThrow());
            assertEquals("access-2", token, "renewed, as it was about to run out");
            assertEquals(Optional.of(a), UiWindow.session(app).call(Auth::account));
        }
    }

    @Test
    void anAnswerThatCannotBeTrustedIsRefused() throws Exception {
        try (App app = App.start()) {
            UiWindow.session(app).storage(StateStorage.memory());
            Auth auth = Auth.configure(app, Auth.Options.of("webview4j-auth-test").provider(keycloak()).browser(browser()).remember(false));
            for (String wrong : List.of("signature", "audience", "nonce", "state")) {
                tamper = wrong;
                CompletionException e = assertThrows(CompletionException.class, () -> auth.signIn("keycloak").join(), wrong);
                assertTrue(e.getCause() instanceof UiException ui && ui.title().contains("trusted"), wrong + ": " + e.getCause());
                assertEquals(Optional.empty(), UiWindow.session(app).call(Auth::account), wrong + ": nobody signed in");
            }
        }
    }

    @Test
    void aRememberedSignInComesBackFromTheVaultAndSigningOutForgetsIt() throws Exception {
        StateStorage disk = StateStorage.memory();
        try (App app = App.start()) {
            UiWindow.session(app).storage(disk);
            Auth.configure(app, Auth.Options.of("webview4j-auth-test").provider(keycloak()).browser(browser()))
                    .signIn("keycloak").get(20, java.util.concurrent.TimeUnit.SECONDS);
            assertTrue(SecretStore.of("webview4j-auth-test").get("keycloak").isPresent(), "the refresh token, in the system's vault");
            UiWindow.session(app).flush();
        }
        try (App app = App.start()) {   // the next start: signed in again, without a browser
            UiWindow.session(app).storage(disk);
            Auth auth = Auth.configure(app, Auth.Options.of("webview4j-auth-test").provider(keycloak()).browser(uri -> fail("no browser needed")));
            for (int i = 0; i < 100 && UiWindow.session(app).call(Auth::account).isEmpty(); i++) Thread.sleep(50);
            assertEquals("Ana Reyes", UiWindow.session(app).call(Auth::account).orElseThrow().name());

            auth.signOut();
            assertEquals(Optional.empty(), UiWindow.session(app).call(Auth::account));
            assertTrue(SecretStore.of("webview4j-auth-test").get("keycloak").isEmpty(), "forgotten in the vault");
            for (int i = 0; i < 50 && revoked.size() < 2; i++) Thread.sleep(50);
            assertTrue(revoked.stream().anyMatch(t -> t.startsWith("refresh-")), "the refresh token revoked: " + revoked);
        }
    }

    @Test
    void namesAndPasswordsCheckedByTheAppsServer() throws Exception {
        try (App app = App.start()) {
            UiWindow.session(app).storage(StateStorage.memory());
            Auth auth = Auth.configure(app, Auth.Options.of("webview4j-auth-test").remember(false).passwords((who, password) ->
                    password.equals("correct horse") ? Optional.of(new Auth.Account("1", "Ben", who, null, java.util.Set.of("staff"), "password"))
                            : Optional.empty()));
            CompletionException wrong = assertThrows(CompletionException.class, () -> auth.signIn("ben@example.com", "nope").join());
            assertTrue(((UiException) wrong.getCause()).userMessage().contains("do not match"));
            assertEquals("Ben", auth.signIn("ben@example.com", "correct horse").join().name());
        }
    }

    // ---- the provider's helpers ----

    static void json(HttpExchange x, Object body) throws java.io.IOException {
        json(x, 200, body);
    }

    static void json(HttpExchange x, int status, Object body) throws java.io.IOException {
        byte[] b = Json.write(body).getBytes(StandardCharsets.UTF_8);
        x.getResponseHeaders().add("Content-Type", "application/json");
        x.sendResponseHeaders(status, b.length);
        x.getResponseBody().write(b);
        x.close();
    }

    static String b64(byte[] b) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    static byte[] unsigned(BigInteger n) {
        byte[] b = n.toByteArray();
        return b[0] == 0 ? java.util.Arrays.copyOfRange(b, 1, b.length) : b;
    }

    static byte[] sha256(byte[] b) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(b);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
