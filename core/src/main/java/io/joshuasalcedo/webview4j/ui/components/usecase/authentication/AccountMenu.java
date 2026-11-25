package io.joshuasalcedo.webview4j.ui.components.usecase.authentication;

import io.joshuasalcedo.webview4j.ui.Component;
import io.joshuasalcedo.webview4j.ui.components.Element;

/**
 * Who is signed in: an avatar (and the name, unless compact) opening a menu with the email, the app's entries,
 * Switch account and Sign out. The Java side of the kit's {@code AccountMenu}; {@link Auth#accountMenu} makes one.
 */
public final class AccountMenu extends Component<AccountMenu> {

    public AccountMenu(String name) {
        super("accountMenu");
        prop("name", name);
    }

    public AccountMenu email(String email) { return prop("email", email); }
    public AccountMenu picture(String url) { return prop("picture", url); }
    /** Only the avatar (a title bar). */
    public AccountMenu compact() { return prop("compact", true); }
    /** An entry of the app's own (Profile, Settings): call again for each. */
    public AccountMenu entry(String label, Runnable action) {
        return add(slot(new Element("menuItem").set("label", label).handle("onSelect", v -> action.run()), "entry"));
    }
    public AccountMenu onSwitch(Runnable action) { return on("onSwitch", v -> action.run()); }
    public AccountMenu onSignOut(Runnable action) { return on("onSignOut", v -> action.run()); }
}
