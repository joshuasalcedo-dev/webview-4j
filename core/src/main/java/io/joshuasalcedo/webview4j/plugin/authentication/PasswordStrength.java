package io.joshuasalcedo.webview4j.plugin.authentication;

import io.joshuasalcedo.webview4j.ui.Component;

/**
 * How strong a new password is, as it is typed: a bar, a word, and what would make it stronger. A guide for sign-up
 * and password changes (the server decides what it takes). The Java side of the kit's {@code PasswordStrength}.
 */
public final class PasswordStrength extends Component<PasswordStrength> {

    public PasswordStrength(String password) {
        super("passwordStrength");
        prop("password", password == null ? "" : password);
    }
}
