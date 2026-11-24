package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;
import java.util.function.Consumer;

/**
 * A section with a title that opens and closes, as JavaFX's {@code TitledPane}: details tucked away, settings in
 * groups. The Java side of the kit's {@code TitledPane}.
 * <pre>{@code
 * new TitledPane("Advanced", proxyField, timeoutField).expanded(false)
 * }</pre>
 */
public final class TitledPane extends Component<TitledPane> {

    public TitledPane(String title, Component<?>... content) {
        super("titledPane");
        prop("title", title);
        add(content);
    }

    /** Open or closed (open unless set). */
    public TitledPane expanded(boolean expanded) { return prop("expanded", expanded); }
    public TitledPane onExpandedChange(Consumer<Boolean> action) { return on("onExpandedChange", v -> action.accept(Boolean.TRUE.equals(v))); }
    /** Always open, no way to close it. */
    public TitledPane notCollapsible() { return prop("collapsible", false); }
}
