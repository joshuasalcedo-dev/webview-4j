package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** VS Code's editor (Monaco): about 80 languages, offline. */
public final class CodeEditor extends Component<CodeEditor> {

    public CodeEditor(String label) {
        super("codeEditor");
        prop("label", label);
    }

    public CodeEditor value(String code) { return prop("value", code); }
    /** Monaco's name for the language ("java", "json"); or {@link #fileName} picks it. */
    public CodeEditor language(String language) { return prop("language", language); }
    public CodeEditor fileName(String fileName) { return prop("fileName", fileName); }
    public CodeEditor readOnly(boolean readOnly) { return prop("readOnly", readOnly); }
    public CodeEditor lineNumbers(boolean on) { return prop("lineNumbers", on); }
    public CodeEditor minimap(boolean on) { return prop("minimap", on); }
    public CodeEditor wordWrap(boolean on) { return prop("wordWrap", on); }
    public CodeEditor onChange(java.util.function.Consumer<String> action) { return on("onChange", v -> action.accept(Keys.text(v))); }
}
