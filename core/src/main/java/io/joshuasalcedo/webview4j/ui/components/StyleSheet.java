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
 */
public final class StyleSheet extends Component<StyleSheet> {

    public StyleSheet(String css) {
        super("styleSheet");
        String text = Objects.requireNonNull(css, "css");
        // it ends a <style> element in no page: it is set as text, but a plain closing tag is refused outright
        if (text.toLowerCase(java.util.Locale.ROOT).contains("</style")) throw new IllegalArgumentException("CSS cannot hold </style");
        prop("css", text);
    }
}
