package io.joshuasalcedo.webview4j.plugin.settings;

import io.joshuasalcedo.webview4j.plugin.theme.Appearance;
import io.joshuasalcedo.webview4j.plugin.theme.ThemeSwitcher;
import io.joshuasalcedo.webview4j.ui.Component;
import io.joshuasalcedo.webview4j.ui.components.Button;
import io.joshuasalcedo.webview4j.ui.components.Dialog;
import io.joshuasalcedo.webview4j.ui.components.Surface;
import io.joshuasalcedo.webview4j.ui.components.Text;
import io.joshuasalcedo.webview4j.ui.components.Tone;
import io.joshuasalcedo.webview4j.ui.icons.Icon;
import java.util.Objects;

/**
 * The app's settings: a Settings button that opens a dialog of sections. The first is Appearance (light, dark or
 * system, and the design spec: {@link ThemeSwitcher}); the app adds its own after it. What is changed takes effect
 * at once, and Close closes it (Escape too).
 * <pre>{@code
 * Settings settings = new Settings()
 *         .section("Account", new TextField("Display name"), new Toggle("Email me about orders"));
 * toolbar.add(settings);              // the button; it opens the dialog
 * menu.item("Preferences...", settings::open);
 * }</pre>
 */
public final class Settings extends Component<Settings> {

    private final Button button = new Button("Settings").icon(Icon.SETTINGS).subtle();
    private final Dialog dialog;
    /** Open now: set by open() and close(), which Close and Escape call too. */
    private volatile boolean open;

    public Settings() {
        this("Settings");
    }

    /** {@code title}: the dialog's title and the button's label ("Preferences", say). */
    public Settings(String title) {
        super("box");
        prop("direction", "row");
        Objects.requireNonNull(title, "title");
        button.setText(title);
        dialog = new Dialog(title).open(false).cancel("Close", this::close);
        dialog.with(new Surface("Appearance",
                new ThemeSwitcher(),
                new Text("Light, dark or as your system is, and the design the app is drawn in. It changes at once.")
                        .tone(Tone.SECONDARY)));
        button.addActionListener(e -> open());
        add(button, dialog);
    }

    /** A section of the app's own, after Appearance: its controls take effect as they change, as the rest. */
    public Settings section(String title, Component<?>... controls) {
        dialog.with(new Surface(Objects.requireNonNull(title, "title"), controls));
        return this;
    }

    /** Opens the dialog (from a menu item, a shortcut); a window showing it draws again. */
    public Settings open() {
        open = true;
        dialog.open(true);
        return this;
    }

    /** Closes the dialog. */
    public Settings close() {
        open = false;
        dialog.open(false);
        return this;
    }

    public boolean isOpen() {
        return open;
    }

    /** No button: opened only by {@link #open()} (a menu's Preferences..., Ctrl+,). */
    public Settings withoutButton() {
        remove(button);
        return this;
    }

    /** The appearance it changes (the app's). */
    public Appearance appearance() {
        return Appearance.current();
    }
}
