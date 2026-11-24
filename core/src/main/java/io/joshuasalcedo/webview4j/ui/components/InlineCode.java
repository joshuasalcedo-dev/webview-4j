package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** A word or two of code in a sentence. */
public final class InlineCode extends Component<InlineCode> {

    public InlineCode(String code) {
        super("inlineCode");
        prop("text", code);
    }

}
