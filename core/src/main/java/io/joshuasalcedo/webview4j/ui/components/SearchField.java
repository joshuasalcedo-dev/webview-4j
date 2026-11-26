package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** A search box, as the system draws one. */
public final class SearchField extends Component<SearchField> {

    public SearchField(String label) {
        super("searchField");
        prop("label", label);
        keep("onChange", "value", Keys::text);
    }

    public SearchField value(String value) { return prop("value", value == null ? "" : value); }
    public SearchField placeholder(String placeholder) { return prop("placeholder", placeholder); }
    public SearchField onChange(java.util.function.Consumer<String> action) { return on("onChange", v -> action.accept(Keys.text(v))); }
    /** Enter pressed: search now. */
    public SearchField onSubmit(java.util.function.Consumer<String> action) { return on("onSubmit", v -> action.accept(Keys.text(v))); }

    // --- as Swing writes it ---

    /** What is in it now: what the user typed, or what was set. */
    public String getText() { return get("value") instanceof String s ? s : ""; }
    /** Puts {@code text} in it; a window showing it draws again. */
    public SearchField setText(String text) { return value(text); }
    /** Runs on each change as the user types; {@code e.getValue()} is the whole text. */
    public SearchField addChangeListener(io.joshuasalcedo.webview4j.ui.event.ChangeListener<String> listener) { return addChange("onChange", listener, Keys::text); }
    public SearchField removeChangeListener(io.joshuasalcedo.webview4j.ui.event.ChangeListener<String> listener) { return unlisten("onChange", listener); }
    /** Runs when Enter is pressed in it (Swing's JTextField action). */
    public SearchField addActionListener(io.joshuasalcedo.webview4j.ui.event.ActionListener listener) { return addAction("onSubmit", listener); }
    public SearchField removeActionListener(io.joshuasalcedo.webview4j.ui.event.ActionListener listener) { return unlisten("onSubmit", listener); }
}
