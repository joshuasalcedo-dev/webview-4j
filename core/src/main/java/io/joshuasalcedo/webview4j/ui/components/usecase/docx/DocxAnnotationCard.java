package io.joshuasalcedo.webview4j.ui.components.usecase.docx;

import io.joshuasalcedo.webview4j.ui.Component;
import java.util.Locale;

/**
 * A comment or a tracked change of a Word document, as a card: who and when, a coloured badge for what it is, the
 * text it is about. The Java side of the kit's {@code DocxAnnotationCard}, which the viewer puts in the margin.
 * <pre>{@code
 * new DocxAnnotationCard(DocxAnnotationCard.Kind.COMMENT, "Is this against last year?")
 *         .author("Jules Park").date("Nov 22, 2026").anchorText("Revenue grew by 12%")
 *         .onClick(() -> chosen = 1)
 * }</pre>
 */
public final class DocxAnnotationCard extends Component<DocxAnnotationCard> {

    /** What it is: a comment (a reply, one marked done) or a kind of tracked change. */
    public enum Kind {
        COMMENT, REPLY, RESOLVED, INSERTION, DELETION, MOVE_FROM, MOVE_TO, FORMAT_CHANGE, PARAGRAPH_FORMAT_CHANGE
    }

    public DocxAnnotationCard(Kind kind, String text) {
        super("docxAnnotationCard");
        prop("kind", kind.name().toLowerCase(Locale.ROOT).replace('_', '-'));
        prop("text", text);
    }

    /** Who wrote it ("Unknown author" if left out). */
    public DocxAnnotationCard author(String author) { return prop("author", author); }

    /** When, as it should read. */
    public DocxAnnotationCard date(String date) { return prop("date", date); }

    /** The commented text, quoted above the comment. */
    public DocxAnnotationCard anchorText(String text) { return prop("anchorText", text); }

    /** The badge's text (the kind's own name unless set). */
    public DocxAnnotationCard badge(String badge) { return prop("badge", badge); }

    /** It is the one chosen (outlined in the accent colour). */
    public DocxAnnotationCard active(boolean active) { return prop("active", active); }

    /** Makes it clickable. */
    public DocxAnnotationCard onClick(Runnable action) { return on("onClick", v -> action.run()); }
}
