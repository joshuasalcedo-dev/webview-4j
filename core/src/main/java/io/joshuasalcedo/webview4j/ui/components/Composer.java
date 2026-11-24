package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** A chat's message box: Enter sends, Shift+Enter a new line; it keeps the message when sending fails. */
public final class Composer extends Component<Composer> {

    public Composer(String label) {
        super("composer");
        prop("label", label);
    }

    public Composer placeholder(String placeholder) { return prop("placeholder", placeholder); }
    /** While a message is on its way. */
    public Composer sending(boolean sending) { return prop("sending", sending); }
    public Composer disabled(boolean disabled) { return prop("disabled", disabled); }
    /** Ctrl/⌘+Enter sends, Enter is a new line. */
    public Composer modEnterSends() { return prop("sendKey", "mod+enter"); }
    public Composer maxLength(int length) { return prop("maxLength", length); }
    /** The message's text, sent. */
    public Composer onSend(java.util.function.Consumer<String> action) { return on("onSend", v -> action.accept(Keys.text(v))); }
}
