package io.joshuasalcedo.webview4j.plugin.authentication;

import com.sun.net.httpserver.HttpServer;
import io.joshuasalcedo.webview4j.Json;
import io.joshuasalcedo.webview4j.ui.UiException;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * OpenID Connect for a desktop app, as RFC 8252 says it is done: the system's browser (never a page inside the app,
 * so the app never sees the password and the person's own browser session and password manager are used), a
 * one-time listener on {@code http://127.0.0.1:<port>} for the redirect, PKCE (S256), a random state and nonce. The ID
 * token is believed only with the provider's signature (its JWKS: RS256 or ES256), its issuer, audience, expiry and
 * nonce. {@link Auth} uses it; this is the part without the screens.
 */
public final class Oidc {

    /** What a provider's discovery document says. */
    public record Discovery(String issuer, URI authorization, URI token, URI jwks, URI revocation, URI endSession) {}

    /** What signing in or renewing gave: the tokens, when the access token runs out, and the ID token's claims. */
    public record Tokens(String accessToken, String refreshToken, String idToken, Instant expiresAt, Map<String, Object> claims) {

        public boolean expiresWithin(Duration margin) {
            return expiresAt != null && Instant.now().plus(margin).isAfter(expiresAt);
        }
    }

    private static final SecureRandom RANDOM = new SecureRandom();
    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(15)).build();
    private final Map<URI, Discovery> discovered = new ConcurrentHashMap<>();
    private final Map<URI, Map<String, PublicKey>> keys = new ConcurrentHashMap<>();
    private Duration wait = Duration.ofMinutes(5);

    /** How long signing in waits for the browser to come back (5 minutes unless set). */
    public Oidc waitFor(Duration wait) {
        this.wait = Objects.requireNonNull(wait, "wait");
        return this;
    }

    public Discovery discover(OidcProvider provider) {
        return discovered.computeIfAbsent(provider.discovery(), uri -> {
            Map<String, Object> d = Json.object(get(uri));
            return new Discovery(String.valueOf(d.get("issuer")), uri(d, "authorization_endpoint"), uri(d, "token_endpoint"),
                    uri(d, "jwks_uri"), d.containsKey("revocation_endpoint") ? uri(d, "revocation_endpoint") : null,
                    d.containsKey("end_session_endpoint") ? uri(d, "end_session_endpoint") : null);
        });
    }

    /**
     * Signs in: {@code browse} opens the provider's page in the system's browser; the future completes when it comes
     * back (or fails: cancelled, refused, too slow, not to be trusted).
     */
    public CompletableFuture<Tokens> signIn(OidcProvider provider, Consumer<URI> browse) {
        return CompletableFuture.supplyAsync(() -> {
            Discovery d = discover(provider);
            String verifier = random(48);
            String challenge = base64(sha256(verifier.getBytes(StandardCharsets.US_ASCII)));
            String state = random(24);
            String nonce = random(24);
            CompletableFuture<Map<String, String>> back = new CompletableFuture<>();
            HttpServer server;
            try {
                server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            } catch (IOException e) {
                throw new UiException("Signing in could not start", "The app could not listen for the browser's answer.", UiException.Level.ERROR, e);
            }
            server.createContext("/callback", exchange -> {
                Map<String, String> q = query(exchange.getRequestURI().getRawQuery());
                boolean ok = q.containsKey("code") && MessageDigest.isEqual(state.getBytes(), q.getOrDefault("state", "").getBytes());
                byte[] page = ("<!doctype html><meta charset=utf-8><title>Signed in</title><body style=\"font:16px system-ui;padding:48px\">"
                        + (ok ? "<h2>You are signed in</h2><p>You can close this tab and go back to the app.</p>"
                              : "<h2>That did not work</h2><p>Go back to the app and try again.</p>") + "</body>").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
                exchange.getResponseHeaders().add("Cache-Control", "no-store");
                exchange.sendResponseHeaders(200, page.length);
                exchange.getResponseBody().write(page);
                exchange.close();
                back.complete(q);
            });
            server.start();
            try {
                URI redirect = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/callback");
                Map<String, String> ask = new LinkedHashMap<>();
                ask.put("response_type", "code");
                ask.put("client_id", provider.clientId());
                ask.put("redirect_uri", redirect.toString());
                ask.put("scope", String.join(" ", provider.scopes()));
                ask.put("state", state);
                ask.put("nonce", nonce);
                ask.put("code_challenge", challenge);
                ask.put("code_challenge_method", "S256");
                browse.accept(URI.create(d.authorization() + (d.authorization().getQuery() == null ? "?" : "&") + form(ask)));
                Map<String, String> answer;
                try {
                    answer = back.get(wait.toMillis(), TimeUnit.MILLISECONDS);
                } catch (java.util.concurrent.TimeoutException e) {
                    throw new UiException("Signing in took too long", "The browser did not come back. Try again.", UiException.Level.INFO, e);
                }
                if (answer.containsKey("error")) {
                    String why = answer.getOrDefault("error_description", answer.get("error"));
                    throw new UiException("You were not signed in", "The sign-in page said: " + why, UiException.Level.WARNING);
                }
                if (!MessageDigest.isEqual(state.getBytes(), answer.getOrDefault("state", "").getBytes())) {
                    throw new UiException("Signing in could not be trusted", "The answer was not for this sign-in, so it was not taken.", UiException.Level.WARNING);
                }
                Map<String, String> exchange = new LinkedHashMap<>();
                exchange.put("grant_type", "authorization_code");
                exchange.put("code", answer.get("code"));
                exchange.put("redirect_uri", redirect.toString());
                exchange.put("client_id", provider.clientId());
                exchange.put("code_verifier", verifier);
                return tokens(provider, d, post(d.token(), exchange), nonce, null);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new UiException("Signing in was stopped", "Try again.", UiException.Level.INFO, e);
            } catch (java.util.concurrent.ExecutionException e) {
                throw new UiException("Signing in failed", "Try again.", UiException.Level.WARNING, e.getCause());
            } finally {
                server.stop(0);
            }
        }, r -> Thread.ofVirtual().name("webview4j-oidc").start(r));
    }

    /** New tokens for a refresh token (the access token about to run out, or a remembered sign-in). */
    public Tokens refresh(OidcProvider provider, String refreshToken) {
        Discovery d = discover(provider);
        Map<String, String> f = new LinkedHashMap<>();
        f.put("grant_type", "refresh_token");
        f.put("refresh_token", refreshToken);
        f.put("client_id", provider.clientId());
        return tokens(provider, d, post(d.token(), f), null, refreshToken);
    }

    /** Tells the provider a token is no longer used (signing out), when it has a revocation endpoint. */
    public void revoke(OidcProvider provider, String token) {
        Discovery d = discover(provider);
        if (d.revocation() == null || token == null) return;
        try {
            post(d.revocation(), Map.of("token", token, "client_id", provider.clientId()));
        } catch (RuntimeException e) {
            // signing out goes on: the token runs out by itself
        }
    }

    private Tokens tokens(OidcProvider provider, Discovery d, Map<String, Object> t, String nonce, String oldRefresh) {
        String id = t.get("id_token") == null ? null : String.valueOf(t.get("id_token"));
        Map<String, Object> claims = id == null ? Map.of() : verify(provider, d, id, nonce);
        Number expires = t.get("expires_in") instanceof Number n ? n : null;
        String refresh = t.get("refresh_token") != null ? String.valueOf(t.get("refresh_token")) : oldRefresh;
        return new Tokens(String.valueOf(t.get("access_token")), refresh, id,
                expires == null ? null : Instant.now().plusSeconds(expires.longValue()), claims);
    }

    // ---- the ID token ----

    /** The ID token's claims, once its signature, issuer, audience, expiry and nonce are right. */
    Map<String, Object> verify(OidcProvider provider, Discovery d, String jwt, String nonce) {
        String[] parts = jwt.split("\\.");
        if (parts.length != 3) throw untrusted("it is not a signed token");
        Map<String, Object> header = Json.object(new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8));
        Map<String, Object> claims = Json.object(new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));
        String alg = String.valueOf(header.get("alg"));
        String javaAlg = switch (alg) {
            case "RS256" -> "SHA256withRSA";
            case "RS384" -> "SHA384withRSA";
            case "RS512" -> "SHA512withRSA";
            case "ES256" -> "SHA256withECDSAinP1363Format";
            case "ES384" -> "SHA384withECDSAinP1363Format";
            default -> throw untrusted("its signature (" + alg + ") is not one this app takes");
        };
        PublicKey key = key(d, String.valueOf(header.get("kid")));
        try {
            Signature s = Signature.getInstance(javaAlg);
            s.initVerify(key);
            s.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            if (!s.verify(Base64.getUrlDecoder().decode(parts[2]))) throw untrusted("its signature is not the provider's");
        } catch (java.security.GeneralSecurityException e) {
            throw untrusted("its signature could not be checked");
        }
        if (!d.issuer().equals(claims.get("iss"))) throw untrusted("it is from another issuer");
        Object aud = claims.get("aud");
        boolean ours = aud instanceof List<?> l ? l.contains(provider.clientId()) : provider.clientId().equals(aud);
        if (!ours) throw untrusted("it is for another app");
        long now = Instant.now().getEpochSecond();
        if (!(claims.get("exp") instanceof Number exp) || exp.longValue() + 60 < now) throw untrusted("it has run out");
        if (nonce != null && !nonce.equals(claims.get("nonce"))) throw untrusted("it is not for this sign-in");
        return Map.copyOf(claims);
    }

    private PublicKey key(Discovery d, String kid) {
        Map<String, PublicKey> known = keys.computeIfAbsent(d.jwks(), this::jwks);
        PublicKey key = known.get(kid);
        if (key == null) {   // keys roll: read them again once
            keys.put(d.jwks(), jwks(d.jwks()));
            key = keys.get(d.jwks()).get(kid);
        }
        if (key == null) throw untrusted("it was signed with a key the provider does not list");
        return key;
    }

    private Map<String, PublicKey> jwks(URI uri) {
        Map<String, PublicKey> out = new LinkedHashMap<>();
        Object list = Json.object(get(uri)).get("keys");
        if (!(list instanceof List<?> all)) return out;
        for (Object o : all) {
            if (!(o instanceof Map<?, ?> k)) continue;
            try {
                String kid = String.valueOf(k.get("kid"));
                if ("RSA".equals(k.get("kty"))) {
                    out.put(kid, KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(big(k.get("n")), big(k.get("e")))));
                } else if ("EC".equals(k.get("kty"))) {
                    String curve = switch (String.valueOf(k.get("crv"))) {
                        case "P-256" -> "secp256r1";
                        case "P-384" -> "secp384r1";
                        default -> null;
                    };
                    if (curve == null) continue;
                    AlgorithmParameters p = AlgorithmParameters.getInstance("EC");
                    p.init(new ECGenParameterSpec(curve));
                    ECParameterSpec spec = p.getParameterSpec(ECParameterSpec.class);
                    out.put(kid, KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(new ECPoint(big(k.get("x")), big(k.get("y"))), spec)));
                }
            } catch (java.security.GeneralSecurityException e) {
                // a key this app cannot use: the others still count
            }
        }
        return out;
    }

    private static BigInteger big(Object base64url) {
        return new BigInteger(1, Base64.getUrlDecoder().decode(String.valueOf(base64url)));
    }

    private static UiException untrusted(String why) {
        return new UiException("Signing in could not be trusted", "The provider's answer was not taken: " + why + ".", UiException.Level.WARNING);
    }

    // ---- HTTP ----

    private String get(URI uri) {
        https(uri);
        try {
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20)).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200) throw new IOException(uri + " answered " + r.statusCode());
            return r.body();
        } catch (IOException e) {
            throw new UiException("The sign-in provider did not answer", "Check the connection, then try again.", UiException.Level.WARNING, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UiException("Signing in was stopped", "Try again.", UiException.Level.INFO, e);
        }
    }

    private Map<String, Object> post(URI uri, Map<String, String> fields) {
        https(uri);
        try {
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/x-www-form-urlencoded").header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(form(fields))).build(), HttpResponse.BodyHandlers.ofString());
            Map<String, Object> body = r.body().isBlank() ? Map.of() : Json.object(r.body());
            if (r.statusCode() / 100 != 2) {
                String why = String.valueOf(body.getOrDefault("error_description", body.getOrDefault("error", "status " + r.statusCode())));
                throw new UiException("You were not signed in", "The provider said: " + why, UiException.Level.WARNING);
            }
            return body;
        } catch (IOException e) {
            throw new UiException("The sign-in provider did not answer", "Check the connection, then try again.", UiException.Level.WARNING, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UiException("Signing in was stopped", "Try again.", UiException.Level.INFO, e);
        }
    }

    /** https only (plain http only to this machine: a provider in tests, a local Keycloak). */
    private static void https(URI uri) {
        if ("https".equalsIgnoreCase(uri.getScheme())) return;
        try {
            if ("http".equalsIgnoreCase(uri.getScheme()) && InetAddress.getByName(uri.getHost()).isLoopbackAddress()) return;
        } catch (IOException e) {
            // not loopback
        }
        throw new UiException("Signing in could not be trusted", "The provider is not reached over https.", UiException.Level.WARNING);
    }

    private static URI uri(Map<String, Object> d, String name) {
        Object v = d.get(name);
        if (v == null) throw new UiException("The sign-in provider is not set up", "Its configuration has no " + name + ".", UiException.Level.ERROR);
        return URI.create(String.valueOf(v));
    }

    static String form(Map<String, String> fields) {
        return fields.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    static Map<String, String> query(String raw) {
        Map<String, String> out = new LinkedHashMap<>();
        if (raw == null) return out;
        for (String pair : raw.split("&")) {
            String[] kv = pair.split("=", 2);
            out.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8), kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "");
        }
        return out;
    }

    private static String random(int bytes) {
        byte[] b = new byte[bytes];
        RANDOM.nextBytes(b);
        return base64(b);
    }

    private static String base64(byte[] b) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static byte[] sha256(byte[] b) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(b);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static Optional<String> claim(Map<String, Object> claims, String name) {
        Object v = claims.get(name);
        return v == null ? Optional.empty() : Optional.of(String.valueOf(v));
    }
}
