package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** Text that belongs to the controls: not selectable, as the systems draw it. */
public final class Label extends Component<Label> {

    public Label(String text) {
        super("label");
        prop("text", text);
    }

    public Label tone(Tone tone) { return prop("tone", tone.key()); }
    public Label strong() { return prop("strong", true); }

    // --- as Swing writes it ---

    /** Says {@code text} now; a window showing it draws again. */
    public Label text(String text) { return prop("text", text); }
    public String getText() { return get("text") instanceof String s ? s : ""; }
    /** Says {@code text} now; a window showing it draws again. */
    public Label setText(String text) { return text(text); }
}
