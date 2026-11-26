package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;
import java.util.Objects;

/**
 * The app's own CSS, for the classes it gives components ({@code className("note")}): anywhere in the screen, it
 * applies to the whole window. The kit's tokens are there to use, so it follows light and dark:
 * <pre>{@code
 * styleSheet("""
 *     .note { color: var(--nt-text-secondary); font-style: italic; }
 *     .totals { border-left: 3px solid var(--nt-accent); padding-left: 12px; }
 *     """)
 * }</pre>
 * Or typed, by the components' parts ({@link io.joshuasalcedo.webview4j.ui.StylePart}), with no CSS text:
 * <pre>{@code
 * new StyleSheet()
 *         .rule(DialogPart.TITLE, Style.of().color(Style.Token.ACCENT))
 *         .rule(ButtonPart.ROOT, Style.of().radius(12))
 * }</pre>
 */
public final class StyleSheet extends Component<StyleSheet> {

    public StyleSheet(String css) {
        super("styleSheet");
        String text = Objects.requireNonNull(css, "css");
        // it ends a <style> element in no page: it is set as text, but a plain closing tag is refused outright
        if (text.toLowerCase(java.util.Locale.ROOT).contains("</style")) throw new IllegalArgumentException("CSS cannot hold </style");
        prop("css", text);
    }

    /** No CSS yet: {@link #rule} adds it, typed. */
    public StyleSheet() {
        this("");
    }

    /** How {@code part} looks in every component of the window that has it, over the kit's look. */
    public StyleSheet rule(io.joshuasalcedo.webview4j.ui.StylePart<?> part, io.joshuasalcedo.webview4j.ui.Style style) {
        java.util.Objects.requireNonNull(part, "part");
        StringBuilder css = new StringBuilder(String.valueOf(get("css")));
        if (!css.isEmpty()) css.append('\n');
        css.append('.').append(part.className()).append(" {");
        // the values were checked by Style.set: no braces, no semicolons, no comments
        java.util.Objects.requireNonNull(style, "style").declarations().forEach((k, v) -> css.append(' ').append(k).append(": ").append(v).append(" !important;"));
        return prop("css", css.append(" }").toString());
    }
}
