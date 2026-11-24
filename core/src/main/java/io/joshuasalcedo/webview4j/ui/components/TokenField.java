package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** Entries as chips (tags, addresses), with suggestions. */
public final class TokenField extends Component<TokenField> {

    public TokenField(String label) {
        super("tokenField");
        prop("label", label);
    }

    public TokenField value(java.util.List<String> tokens) { return prop("value", java.util.List.copyOf(tokens)); }
    public TokenField suggestions(java.util.List<String> suggestions) { return prop("suggestions", java.util.List.copyOf(suggestions)); }
    public TokenField placeholder(String placeholder) { return prop("placeholder", placeholder); }
    public TokenField onChange(java.util.function.Consumer<java.util.List<String>> action) { return on("onChange", v -> action.accept(Keys.texts(v))); }
}
