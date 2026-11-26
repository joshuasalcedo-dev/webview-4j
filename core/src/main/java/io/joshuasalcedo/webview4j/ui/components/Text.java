package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** Text, at the system's sizes and colours. */
public final class Text extends Component<Text> {

    public Text(String text) {
        super("text");
        prop("text", text);
    }

    /** Says this now; a window showing it draws again. */
    public Text text(String text) { return prop("text", text); }
    public Text variant(TextVariant variant) { return prop("variant", variant.key()); }
    public Text tone(Tone tone) { return prop("tone", tone.key()); }
    public Text strong() { return prop("strong", true); }
    public Text mono() { return prop("mono", true); }
    /** Cut off after this many lines, with the whole text as a tooltip. */
    public Text lines(int lines) { return prop("lines", lines); }

    // --- as Swing writes it ---

    public String getText() { return get("text") instanceof String s ? s : ""; }
    /** Says {@code text} now; a window showing it draws again. */
    public Text setText(String text) { return text(text); }
}
