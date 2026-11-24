package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** Code shown with its colours, copyable. */
public final class CodeBlock extends Component<CodeBlock> {

    public CodeBlock(String code, String language) {
        super("code");
        prop("code", code);
        prop("language", language);
    }

}
