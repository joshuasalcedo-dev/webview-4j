package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/**
 * Text that flows as one paragraph, with code, links and words in bold inside it: "Run {@code ./gradlew run} or
 * read the guide."
 * <pre>{@code
 * paragraph("Run ", inlineCode("./gradlew run"), " or read ", link("the guide", url), ".")
 * new Paragraph().text("Saved ").strong("3 files").text(" just now.")
 * }</pre>
 */
public final class Paragraph extends Component<Paragraph> {

    /** Its parts in order: text as text, components (inline code, a link, a pill) as themselves. */
    public Paragraph(Object... parts) {
        super("paragraph");
        for (Object part : parts) {
            if (part instanceof Component<?> c) add(c);
            else text(String.valueOf(part));
        }
    }

    public Paragraph text(String text) { return add(new Element("span").set("text", text)); }
    public Paragraph strong(String text) { return add(new Element("span").set("text", text).set("strong", true)); }
    public Paragraph italic(String text) { return add(new Element("span").set("text", text).set("italic", true)); }
    /** A component inside the text: inline code, a link, a pill. */
    public Paragraph with(Component<?> inline) { return add(inline); }
    public Paragraph variant(TextVariant variant) { return prop("variant", variant.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-')); }
    public Paragraph tone(Tone tone) { return prop("tone", tone.name().toLowerCase(java.util.Locale.ROOT)); }
}
