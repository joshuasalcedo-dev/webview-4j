package io.joshuasalcedo.webview4j.ui.components.usecase.autoupdate;

import io.joshuasalcedo.webview4j.ui.Component;
import io.joshuasalcedo.webview4j.ui.Host;
import java.util.Locale;

/**
 * A new version is there: what it is, what changed in it, and Update now, Later, Skip this version; while it
 * downloads, how far; once checked, Install and restart. The Java side of the kit's {@code UpdatePrompt}.
 * {@link AutoUpdate} shows one by itself; a screen can show its own.
 */
public final class UpdatePrompt extends Component<UpdatePrompt> {

    /** Where the update is. */
    public enum Status { AVAILABLE, DOWNLOADING, READY, INSTALLING, FAILED }

    public UpdatePrompt(String app, String version) {
        super("updatePrompt");
        prop("app", app);
        prop("version", version);
        on("onLink", v -> Host.current().openUrl(String.valueOf(v)));
    }

    /** The version the app is now. */
    public UpdatePrompt current(String version) { return prop("current", version); }
    /** The new version's notes (Markdown). */
    public UpdatePrompt notes(String markdown) { return prop("notes", markdown); }
    public UpdatePrompt status(Status status) { return prop("status", status.name().toLowerCase(Locale.ROOT)); }
    /** 0 to 1 while downloading. */
    public UpdatePrompt progress(double progress) { return prop("progress", progress); }
    public UpdatePrompt error(String message) { return prop("error", message); }
    public UpdatePrompt onUpdate(Runnable action) { return on("onUpdate", v -> action.run()); }
    public UpdatePrompt onInstall(Runnable action) { return on("onInstall", v -> action.run()); }
    public UpdatePrompt onLater(Runnable action) { return on("onLater", v -> action.run()); }
    public UpdatePrompt onSkip(Runnable action) { return on("onSkip", v -> action.run()); }
}
