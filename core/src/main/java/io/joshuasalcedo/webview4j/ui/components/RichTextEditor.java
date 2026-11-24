package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** Formatted text (bold, lists, links, headings): a WordPad-style bar on Windows, TextEdit's on a Mac. Its value is HTML. */
public final class RichTextEditor extends Component<RichTextEditor> {

    public RichTextEditor(String label) {
        super("richText");
        prop("label", label);
    }

    public RichTextEditor value(String html) { return prop("value", html); }
    public RichTextEditor placeholder(String placeholder) { return prop("placeholder", placeholder); }
    public RichTextEditor readOnly(boolean readOnly) { return prop("readOnly", readOnly); }
    public RichTextEditor minHeight(double height) { return prop("minHeight", height); }
    /** The text as HTML, as it changes. */
    public RichTextEditor onChange(java.util.function.Consumer<String> action) { return on("onChange", v -> action.accept(Keys.text(v))); }
}
