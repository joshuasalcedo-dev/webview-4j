package io.joshuasalcedo.webview4j.ui.components.usecase.autoupdate;

import io.joshuasalcedo.webview4j.ui.Component;
import io.joshuasalcedo.webview4j.ui.Host;
import java.util.function.Consumer;

/**
 * A changelog, as people read it: each version with its date and its changes (Markdown). The Java side of the kit's
 * {@code Changelog}.
 * <pre>{@code
 * Changelog.bundled()                        // the app's CHANGELOG.md, put in its jar by the build
 * Changelog.bundled().since("0.2.0")         // What's new since the version last run
 * Changelog.of(markdown).only("0.3.0")       // one version's notes
 * }</pre>
 */
public final class Changelog extends Component<Changelog> {

    public Changelog(String markdown) {
        super("changelog");
        prop("markdown", markdown == null ? "" : markdown);
        on("onLink", v -> Host.current().openUrl(String.valueOf(v)));
    }

    public static Changelog of(String markdown) {
        return new Changelog(markdown);
    }

    /** The app's own changelog (its CHANGELOG.md, put in its jar by the build). */
    public static Changelog bundled() {
        return new Changelog(ReleaseNotes.bundled().markdown());
    }

    /** Only the versions after this one. */
    public Changelog since(String version) { return prop("since", version); }

    /** Only this version. */
    public Changelog only(String version) { return prop("only", version); }

    /** Not the entries newer than this version (an "unreleased" one, ahead of the app). */
    public Changelog upTo(String version) { return prop("upTo", version); }

    public Changelog empty(String text) { return prop("empty", text); }

    /** A link was clicked (instead of opening it in the browser). */
    public Changelog onLink(Consumer<String> action) {
        return on("onLink", v -> action.accept(String.valueOf(v)));
    }
}
