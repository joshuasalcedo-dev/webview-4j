package io.joshuasalcedo.webview4j.plugin.chat;

import io.joshuasalcedo.webview4j.ui.Component;
import java.util.List;

/** Someone is typing: the three dots, and who ("Ana is typing…"). */
public final class TypingIndicator extends Component<TypingIndicator> {

    public TypingIndicator(String... names) {
        super("typing");
        prop("people", List.of(names));
    }
}
