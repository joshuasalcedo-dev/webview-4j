package io.joshuasalcedo.webview4j.plugin.authentication;

import io.joshuasalcedo.webview4j.ui.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Signing in: an email (or a user name) and a password, and providers' buttons (the system's browser opens for them),
 * with what went wrong said to the person and a busy state. The Java side of the kit's {@code SignInForm};
 * {@link Auth#signInForm} makes one wired to the app's providers.
 */
public final class SignInForm extends Component<SignInForm> {

    private final List<Map<String, Object>> providers = new ArrayList<>();

    public SignInForm(String title) {
        super("signInForm");
        prop("title", title);
        prop("providers", providers);
    }

    public SignInForm subtitle(String text) { return prop("subtitle", text); }
    /** A user name instead of an email. */
    public SignInForm username() { return prop("identifier", "username"); }
    /** Passwords here (on unless set: off when only providers sign in). */
    public SignInForm password(boolean on) { return prop("password", on); }
    /** A provider's button: {@code onProvider} gets its id. */
    public SignInForm provider(String id, String label) {
        providers.add(Map.of("id", id, "label", label));
        return this;
    }
    public SignInForm busy(boolean busy) { return prop("busy", busy); }
    public SignInForm error(String message) { return prop("error", message); }
    /** Remember me, offered, and whether it is ticked. */
    public SignInForm remember(boolean on) { return prop("remember", on); }
    public SignInForm onRememberChange(Consumer<Boolean> action) { return on("onRememberChange", v -> action.accept(Boolean.TRUE.equals(v))); }
    /** What was typed: the email (or user name) and the password. */
    public SignInForm onSubmit(BiConsumer<String, String> action) {
        return on("onSubmit", v -> {
            Map<?, ?> m = v instanceof Map<?, ?> map ? map : Map.of();
            action.accept(String.valueOf(m.get("identifier")), String.valueOf(m.get("password")));
        });
    }
    public SignInForm onProvider(Consumer<String> action) { return on("onProvider", v -> action.accept(String.valueOf(v))); }
    public SignInForm onForgot(Runnable action) { return on("onForgot", v -> action.run()); }
}
