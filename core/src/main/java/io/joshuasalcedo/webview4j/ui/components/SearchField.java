package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** A search box, as the system draws one. */
public final class SearchField extends Component<SearchField> {

    public SearchField(String label) {
        super("searchField");
        prop("label", label);
    }

    public SearchField value(String value) { return prop("value", value == null ? "" : value); }
    public SearchField placeholder(String placeholder) { return prop("placeholder", placeholder); }
    public SearchField onChange(java.util.function.Consumer<String> action) { return on("onChange", v -> action.accept(Keys.text(v))); }
    /** Enter pressed: search now. */
    public SearchField onSubmit(java.util.function.Consumer<String> action) { return on("onSubmit", v -> action.accept(Keys.text(v))); }
}
