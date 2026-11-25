package io.joshuasalcedo.webview4j.ui.components.usecase.authentication;

import io.joshuasalcedo.webview4j.ui.Component;

/**
 * The sign-in ran out while the person was working: a dialog over the screen (what is on it stays) to sign in again or
 * to leave. The Java side of the kit's {@code SessionExpired}; {@link Auth#sessionExpired} makes one that shows itself.
 */
public final class SessionExpired extends Component<SessionExpired> {

    public SessionExpired(boolean open) {
        super("sessionExpired");
        prop("open", open);
    }

    /** Who was signed in. */
    public SessionExpired name(String name) { return prop("name", name); }
    public SessionExpired busy(boolean busy) { return prop("busy", busy); }
    public SessionExpired onSignIn(Runnable action) { return on("onSignIn", v -> action.run()); }
    public SessionExpired onSignOut(Runnable action) { return on("onSignOut", v -> action.run()); }
}
