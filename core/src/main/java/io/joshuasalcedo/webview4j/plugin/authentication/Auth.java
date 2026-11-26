package io.joshuasalcedo.webview4j.plugin.authentication;

import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.SecretStore;
import io.joshuasalcedo.webview4j.ui.Host;
import io.joshuasalcedo.webview4j.ui.UiException;
import io.joshuasalcedo.webview4j.ui.UiWindow;
import io.joshuasalcedo.webview4j.ui.state.Codec;
import io.joshuasalcedo.webview4j.ui.state.Saved;
import io.joshuasalcedo.webview4j.ui.state.Session;
import io.joshuasalcedo.webview4j.ui.state.Store;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Who is signed in, for the whole app: sign-in through a provider (OpenID Connect, in the system's browser) or with a
 * name and password checked by the app's own server, the account in every screen ({@link #account()}), a valid
 * access token for calls ({@link #bearer()}), renewed before it runs out, and the sign-in remembered in the system's
 * vault (never in a file) for the next start.
 * <pre>{@code
 * Auth auth = Auth.configure(app, Auth.Options.of("Office")
 *         .provider(OidcProvider.keycloak("https://sso.example.com", "office", "office-desktop")));
 *
 * public Component<?> render() {
 *     return Auth.account().isEmpty() ? auth.signInForm("Sign in to Office") : office();
 * }
 * ServerState.of("orders", codec, () -> api.orders(Auth.bearer().orElseThrow()))
 * }</pre>
 */
public final class Auth {

    /** Someone signed in. */
    public record Account(String id, String name, String email, String picture, Set<String> roles, String provider) {
        public Account {
            roles = Set.copyOf(roles == null ? Set.of() : roles);
        }

        public boolean has(String role) {
            return roles.contains(role);
        }
    }

    /** A name and password checked by the app's own server: the account, or empty when they do not match. */
    @FunctionalInterface
    public interface PasswordCheck {
        Optional<Account> check(String identifier, String password) throws Exception;
    }

    /** How the app signs people in. */
    public record Options(String app, List<OidcProvider> providers, PasswordCheck passwords, boolean remember, Consumer<URI> browser) {

        /** For the app named {@code app} (its vault entries are under that name). */
        public static Options of(String app) {
            return new Options(app, List.of(), null, true, null);
        }

        public Options provider(OidcProvider provider) {
            List<OidcProvider> all = new ArrayList<>(providers);
            all.add(provider);
            return new Options(app, all, passwords, remember, browser);
        }

        /** Names and passwords, checked by the app's own server. */
        public Options passwords(PasswordCheck check) {
            return new Options(app, providers, check, remember, browser);
        }

        /** Whether a provider's sign-in is kept for the next start (in the system's vault; on unless set). */
        public Options remember(boolean remember) {
            return new Options(app, providers, passwords, remember, browser);
        }

        /** How the provider's page is opened (the system's browser unless set: tests open it themselves). */
        public Options browser(Consumer<URI> browser) {
            return new Options(app, providers, passwords, remember, browser);
        }
    }

    private static final Store<Account> ACCOUNT = Store.of("auth.account", Codec.of(Account.class), () -> null);
    private static final Store<Boolean> EXPIRED = Store.of("auth.expired", Codec.of(Boolean.class), () -> false);
    private static final Map<Session, Auth> ALL = new java.util.concurrent.ConcurrentHashMap<>();

    private final Session session;
    private final Options options;
    private final Oidc oidc = new Oidc();
    private final SecretStore vault;
    private final Saved<String> lastProvider = Saved.of("auth.provider", String.class, () -> null);
    private volatile OidcProvider provider;
    private volatile Oidc.Tokens tokens;
    private volatile String busy;
    private volatile String error;

    private Auth(Session session, Options options) {
        this.session = session;
        this.options = options;
        this.vault = SecretStore.of(options.app());
    }

    /** The desktop app's sign-in: one for its session; a remembered sign-in comes back in the background. */
    public static Auth configure(App app, Options options) {
        Session session = UiWindow.session(app);
        Auth auth = new Auth(session, Objects.requireNonNull(options, "options"));
        ALL.put(session, auth);
        if (options.remember()) Thread.ofVirtual().name("webview4j-auth-restore").start(auth::restore);
        return auth;
    }

    /** The current session's sign-in (in a screen's render and handlers). */
    public static Optional<Auth> current() {
        try {
            return Optional.ofNullable(ALL.get(Session.current()));
        } catch (IllegalStateException e) {
            return Optional.empty();
        }
    }

    /** Who is signed in now; a screen reading it is drawn again when it changes. */
    public static Optional<Account> account() {
        try {
            return Optional.ofNullable(ACCOUNT.get());
        } catch (IllegalStateException e) {
            return Optional.empty();
        }
    }

    /** A valid access token for a call (renewed if it runs out within a minute); empty when nobody is signed in. */
    public static Optional<String> bearer() {
        return current().flatMap(Auth::token);
    }

    // ---- signing in ----

    /** Signs in with a provider: the system's browser opens its page. */
    public CompletableFuture<Account> signIn(String providerId) {
        OidcProvider p = options.providers().stream().filter(x -> x.id().equals(providerId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no provider " + providerId));
        busy = providerId;
        error = null;
        changed();
        Consumer<URI> browse = options.browser() != null ? options.browser() : uri -> Host.current().openUrl(uri.toString());
        Consumer<URI> inSession = uri -> session.call(() -> {
            browse.accept(uri);
            return null;
        });
        return oidc.signIn(p, inSession).thenApply(t -> signedIn(p, t)).whenComplete((a, e) -> {
            busy = null;
            if (e != null) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                error = cause instanceof UiException ui ? ui.userMessage() : "Signing in did not work. Try again.";
            }
            changed();
        });
    }

    /** Signs in with a name and password, checked by the app's own server. */
    public CompletableFuture<Account> signIn(String identifier, String password) {
        if (options.passwords() == null) return CompletableFuture.failedFuture(new IllegalStateException("no passwords: Options.passwords(check)"));
        busy = "password";
        error = null;
        changed();
        return CompletableFuture.supplyAsync(() -> {
            try {
                Optional<Account> a = options.passwords().check(identifier, password);
                if (a.isEmpty()) {
                    Thread.sleep(400);   // a little slower for each wrong try
                    throw new UiException("You were not signed in", "That name and password do not match.", UiException.Level.INFO);
                }
                set(a.get());
                return a.get();
            } catch (UiException e) {
                throw e;
            } catch (Exception e) {
                throw new UiException("You were not signed in", "The server could not check it. Try again.", UiException.Level.WARNING, e);
            }
        }, r -> Thread.ofVirtual().start(r)).whenComplete((a, e) -> {
            busy = null;
            if (e != null) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                error = cause instanceof UiException ui ? ui.userMessage() : "Signing in did not work.";
            }
            changed();
        });
    }

    private Account signedIn(OidcProvider p, Oidc.Tokens t) {
        provider = p;
        tokens = t;
        Account a = account(p, t.claims());
        if (options.remember() && t.refreshToken() != null) {
            vault.put(p.id(), t.refreshToken());
            session.call(() -> {
                lastProvider.set(p.id());
                return null;
            });
            session.flush();
        }
        set(a);
        return a;
    }

    /** A remembered sign-in, back: the refresh token from the vault, renewed. False if there was none (or it ran out). */
    public boolean restore() {
        String id = session.call(lastProvider::get);
        if (id == null) return false;
        Optional<OidcProvider> p = options.providers().stream().filter(x -> x.id().equals(id)).findFirst();
        Optional<String> refresh = p.isEmpty() ? Optional.empty() : vault.get(id);
        if (refresh.isEmpty()) return false;
        try {
            Oidc.Tokens t = oidc.refresh(p.get(), refresh.get());
            signedIn(p.get(), t);
            return true;
        } catch (RuntimeException e) {
            vault.delete(id);   // no longer good: the person signs in again
            return false;
        }
    }

    /** Signs out: the tokens revoked, the remembered sign-in deleted from the vault, the account gone. */
    public void signOut() {
        OidcProvider p = provider;
        Oidc.Tokens t = tokens;
        provider = null;
        tokens = null;
        if (p != null) {
            Thread.ofVirtual().start(() -> {
                if (t != null) {
                    oidc.revoke(p, t.refreshToken());
                    oidc.revoke(p, t.accessToken());
                }
            });
            vault.delete(p.id());
        }
        session.call(() -> {
            lastProvider.reset();
            return null;
        });
        set(null);
    }

    /** The server said 401: the token is renewed if it can be; else the session has expired (SessionExpired shows). */
    public void unauthorized() {
        OidcProvider p = provider;
        Oidc.Tokens t = tokens;
        if (p != null && t != null && t.refreshToken() != null) {
            try {
                tokens = oidc.refresh(p, t.refreshToken());
                return;
            } catch (RuntimeException e) {
                // renewing failed: sign in again
            }
        }
        session.call(() -> {
            EXPIRED.set(true);
            return null;
        });
    }

    private Optional<String> token() {
        Oidc.Tokens t = tokens;
        OidcProvider p = provider;
        if (t == null) return Optional.empty();
        if (t.expiresWithin(Duration.ofMinutes(1)) && t.refreshToken() != null && p != null) {
            try {
                tokens = t = oidc.refresh(p, t.refreshToken());
            } catch (RuntimeException e) {
                unauthorized();
                return Optional.empty();
            }
        }
        return Optional.of(t.accessToken());
    }

    public Options options() {
        return options;
    }

    // ---- the account, from the ID token ----

    static Account account(OidcProvider p, Map<String, Object> claims) {
        String email = Oidc.claim(claims, "email").orElse(null);
        String name = Oidc.claim(claims, "name").or(() -> Oidc.claim(claims, "preferred_username")).orElse(email != null ? email : "Signed in");
        Set<String> roles = new LinkedHashSet<>();
        addAll(roles, claims.get("roles"));
        addAll(roles, claims.get("groups"));
        if (claims.get("realm_access") instanceof Map<?, ?> realm) addAll(roles, realm.get("roles"));   // Keycloak
        return new Account(Oidc.claim(claims, "sub").orElse(name), name, email, Oidc.claim(claims, "picture").orElse(null), roles, p.id());
    }

    private static void addAll(Set<String> into, Object list) {
        if (list instanceof List<?> l) for (Object o : l) into.add(String.valueOf(o));
    }

    private void set(Account a) {
        session.call(() -> {
            ACCOUNT.set(a);
            if (a != null) EXPIRED.set(false);
            return null;
        });
    }

    private void changed() {
        session.call(() -> {
            // the busy and error states are read by the sign-in form: who shows it is drawn again
            BUSY.set(busy == null ? "" : busy);
            ERROR.set(error == null ? "" : error);
            return null;
        });
    }

    private static final Store<String> BUSY = Store.of("auth.busy", String.class, () -> "");
    private static final Store<String> ERROR = Store.of("auth.error", String.class, () -> "");

    // ---- ready-made pieces ----

    /** The sign-in form for this app's providers (and passwords, if it has them): busy and errors included. */
    public SignInForm signInForm(String title) {
        SignInForm form = new SignInForm(title).password(options.passwords() != null).busy(!BUSY.get().isEmpty());
        if (!ERROR.get().isEmpty()) form.error(ERROR.get());
        for (OidcProvider p : options.providers()) form.provider(p.id(), p.label());
        return form.onProvider(id -> signIn(id)).onSubmit((who, password) -> signIn(who, password));
    }

    /** The account menu for who is signed in (nothing when nobody is). */
    public Optional<AccountMenu> accountMenu() {
        return account().map(a -> new AccountMenu(a.name()).email(a.email()).picture(a.picture()).onSignOut(this::signOut));
    }

    /** The "sign in again" dialog: shown once the session has expired. */
    public SessionExpired sessionExpired() {
        String name = account().map(Account::name).orElse(null);
        String last = session.call(lastProvider::get);
        return new SessionExpired(EXPIRED.get()).name(name)
                .onSignIn(() -> {
                    if (last != null) signIn(last);
                })
                .onSignOut(this::signOut);
    }
}
