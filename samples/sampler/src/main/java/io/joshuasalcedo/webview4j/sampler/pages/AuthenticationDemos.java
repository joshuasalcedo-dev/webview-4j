package io.joshuasalcedo.webview4j.sampler.pages;

import static io.joshuasalcedo.webview4j.ui.Ui.*;

import io.joshuasalcedo.webview4j.ui.components.Tone;
import io.joshuasalcedo.webview4j.ui.components.usecase.authentication.AccountMenu;
import io.joshuasalcedo.webview4j.ui.components.usecase.authentication.CodeField;
import io.joshuasalcedo.webview4j.ui.components.usecase.authentication.PasswordStrength;
import io.joshuasalcedo.webview4j.ui.components.usecase.authentication.SessionExpired;
import io.joshuasalcedo.webview4j.ui.components.usecase.authentication.SignInForm;

/** The Sampler's pages: signing in, a one-time code, a new password's strength, the account, a sign-in run out. */
final class AuthenticationDemos implements Demos {

    private String said = "Type anything; the password is correct horse";
    private boolean busy;
    private String code = "";
    private String password = "";
    private boolean expired;
    private boolean signedIn = true;

    @Override
    public void addTo(Gallery gallery) {
        gallery.page("Authentication", "Sign-in form", () -> column(
                        new SignInForm("Sign in to Office").subtitle("With your work account.")
                                .provider("keycloak", "Sign in with Keycloak").provider("microsoft", "Sign in with Microsoft")
                                .busy(busy).error(said.startsWith("That") ? said : null)
                                .onProvider(id -> said = "The system's browser opens for " + id + " (Auth.signIn(\"" + id + "\"))")
                                .onSubmit((who, secret) -> said = secret.equals("correct horse") ? "Signed in as " + who : "That email and password do not match.")
                                .onForgot(() -> said = "A link to reset it is on its way"),
                        text(said).tone(Tone.SECONDARY)),
                """
                Auth auth = Auth.configure(app, Auth.Options.of("Office")
                        .provider(OidcProvider.keycloak("https://sso.example.com", "office", "office-desktop")));
                auth.signInForm("Sign in to Office")     // the providers' buttons, busy and errors wired""",
                SignInForm.class);
        gallery.page("Authentication", "One-time code", () -> column(
                        new CodeField("Code from your app").value(code).onChange(v -> code = v).onComplete(c -> said = "Code " + c + " sent"),
                        text(code.length() == 6 ? "Complete: " + code : code.length() + " of 6").tone(Tone.SECONDARY)),
                """
                new CodeField("Code from your app").onComplete(code -> verify(code))""",
                CodeField.class);
        gallery.page("Authentication", "Password strength", () -> column(
                        textField("New password").password().value(password).onChange(v -> password = v),
                        new PasswordStrength(password)),
                """
                textField("New password").password().onChange(v -> password = v)
                new PasswordStrength(password)""",
                PasswordStrength.class);
        gallery.page("Authentication", "Account menu", () -> column(
                        signedIn
                                ? row(new AccountMenu("Ana Reyes").email("ana@example.com").entry("Profile", () -> said = "Profile")
                                        .entry("Settings", () -> said = "Settings").onSwitch(() -> said = "Switch account").onSignOut(() -> signedIn = false),
                                        new AccountMenu("Ana Reyes").compact().onSignOut(() -> signedIn = false)).gap(16)
                                : button("Sign in again").accent().onClick(() -> signedIn = true),
                        text("Click it, or right-click").tone(Tone.SECONDARY)),
                """
                auth.accountMenu()   // who is signed in, with Sign out
                new AccountMenu("Ana Reyes").email("ana@example.com").entry("Profile", ...).onSignOut(...)""",
                AccountMenu.class);
        gallery.page("Authentication", "Session expired", () -> column(
                        button("Let the sign-in run out").onClick(() -> expired = true),
                        new SessionExpired(expired).name("Ana Reyes").onSignIn(() -> expired = false).onSignOut(() -> expired = false)),
                """
                auth.sessionExpired()   // shows itself when the server says 401 and the token cannot be renewed""",
                SessionExpired.class);
    }
}
