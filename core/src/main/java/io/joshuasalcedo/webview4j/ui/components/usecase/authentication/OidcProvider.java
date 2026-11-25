package io.joshuasalcedo.webview4j.ui.components.usecase.authentication;

import java.net.URI;
import java.util.List;
import java.util.Objects;

/**
 * A sign-in provider that speaks OpenID Connect: its issuer (where its {@code .well-known/openid-configuration} is),
 * the app's client id there, and the scopes asked for. A desktop app is a public client: no secret, PKCE instead
 * (RFC 8252); the provider's client is set up with the redirect {@code http://127.0.0.1} (any port).
 * <pre>{@code
 * OidcProvider.keycloak("https://sso.example.com", "office", "office-desktop")
 * OidcProvider.microsoft("<tenant id>", "<application id>")
 * OidcProvider.of("auth0", "Auth0", URI.create("https://example.eu.auth0.com/"), "<client id>")
 * }</pre>
 */
public record OidcProvider(String id, String label, URI issuer, String clientId, List<String> scopes) {

    public OidcProvider {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(issuer, "issuer");
        Objects.requireNonNull(clientId, "clientId");
        scopes = List.copyOf(scopes == null || scopes.isEmpty() ? List.of("openid", "profile", "email", "offline_access") : scopes);
        if (!scopes.contains("openid")) throw new IllegalArgumentException("OpenID Connect asks for the openid scope");
    }

    public static OidcProvider of(String id, String label, URI issuer, String clientId) {
        return new OidcProvider(id, label, issuer, clientId, null);
    }

    /** A Keycloak realm: {@code https://<host>/realms/<realm>}. */
    public static OidcProvider keycloak(String server, String realm, String clientId) {
        return of("keycloak", "Sign in with Keycloak", URI.create(server.replaceAll("/+$", "") + "/realms/" + realm), clientId);
    }

    /** Microsoft Entra ID (a work or school account), of one tenant. */
    public static OidcProvider microsoft(String tenant, String clientId) {
        return of("microsoft", "Sign in with Microsoft", URI.create("https://login.microsoftonline.com/" + tenant + "/v2.0"), clientId);
    }

    /** Google accounts. */
    public static OidcProvider google(String clientId) {
        return of("google", "Sign in with Google", URI.create("https://accounts.google.com"), clientId);
    }

    /** The same provider asking for other scopes ({@code openid} always among them). */
    public OidcProvider scopes(String... scopes) {
        return new OidcProvider(id, label, issuer, clientId, List.of(scopes));
    }

    URI discovery() {
        String base = issuer.toString().replaceAll("/+$", "");
        return URI.create(base + "/.well-known/openid-configuration");
    }
}
