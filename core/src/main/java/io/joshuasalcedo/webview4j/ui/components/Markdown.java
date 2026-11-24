package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;
import io.joshuasalcedo.webview4j.ui.Host;
import java.util.function.Consumer;

/**
 * Markdown, in the kit's look: CommonMark with GitHub's tables, task lists and strikethrough. Safe for text from
 * anywhere: HTML in it is shown as text, never run; only http, https and mailto links are kept. A link opens in the
 * browser ({@link Host#openUrl}) unless {@link #onLink} says otherwise. The Java side of the kit's {@code Markdown}.
 * <pre>{@code
 * markdown("""
 *         ## What's new
 *         - Layout panes, named as JavaFX's
 *         - State kept on disk""")
 * }</pre>
 */
public final class Markdown extends Component<Markdown> {

    public Markdown(String text) {
        super("markdown");
        prop("text", text == null ? "" : text);
        on("onLink", v -> Host.current().openUrl(String.valueOf(v)));
    }

    /** Headings start at this level (2: a "# Title" is shown as a second-level heading), inside a page with a title. */
    public Markdown headingOffset(int offset) {
        return prop("headingOffset", offset);
    }

    /** A link was clicked: where it goes (instead of opening it in the browser). */
    public Markdown onLink(Consumer<String> action) {
        return on("onLink", v -> action.accept(String.valueOf(v)));
    }
}
