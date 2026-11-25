package io.joshuasalcedo.webview4j.sampler.pages;

import static io.joshuasalcedo.webview4j.ui.Ui.*;

import io.joshuasalcedo.webview4j.ui.components.Tone;
import io.joshuasalcedo.webview4j.ui.components.usecase.docx.DocumentViewerSidebar;
import io.joshuasalcedo.webview4j.ui.components.usecase.docx.DocxAnnotationCard;
import io.joshuasalcedo.webview4j.ui.components.usecase.docx.DocxDocument;
import io.joshuasalcedo.webview4j.ui.components.usecase.docx.DocxEditor;
import io.joshuasalcedo.webview4j.ui.components.usecase.docx.DocxLayoutBlocks;
import io.joshuasalcedo.webview4j.ui.components.usecase.docx.DocxViewer;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/** The Sampler's pages: Word documents, seen, edited, their side panel, their comment cards and their layout blocks. */
final class DocxDemos implements Demos {

    /** A quarterly report: headings, lists, two tables, a link, a comment, tracked changes, two sections. */
    private static final byte[] REPORT = resource("/samples/sample.docx");

    private byte[] edited = REPORT;
    private boolean dirty;
    private boolean readOnly;
    private String said = "Ctrl+S (⌘S on a Mac) saves";
    private int page = 1;
    private String heading = "summary";
    private int card = 0;
    private String block = "Click a block";

    @Override
    public void addTo(Gallery gallery) {
        gallery.page("Documents", "Word viewer", () -> column(
                        new DocxViewer().bytes(REPORT).fileName("Quarterly report.docx").height(560)
                                .onPageChange(p -> page = p)
                                .onDownload((name, bytes) -> said = name + ": " + bytes.length + " bytes, for Java to save"),
                        text("Page " + page + " · " + said).tone(Tone.SECONDARY)),
                """
                new DocxViewer().file(Path.of("report.docx")).height(560)
                        .onPageChange(p -> page = p)
                        .onDownload((name, bytes) -> Files.write(Path.of(name), bytes))""",
                DocxViewer.class);
        gallery.page("Documents", "Word editor", () -> column(
                        new DocxEditor().bytes(edited).fileName("Quarterly report.docx").height(600)
                                .readOnly(readOnly).onReadOnlyChange(v -> readOnly = v)
                                .onDirtyChange(d -> dirty = d)
                                .onSave((name, bytes) -> {
                                    edited = bytes;
                                    said = "Saved " + name + ": " + bytes.length + " bytes";
                                }),
                        row(toggle("Read only").checked(readOnly).onChange(v -> readOnly = v),
                                text(dirty ? "Changed, not saved" : said).tone(Tone.SECONDARY))),
                """
                new DocxEditor().bytes(document).fileName("Quarterly report.docx")
                        .readOnly(readOnly).onReadOnlyChange(v -> readOnly = v)
                        .onDirtyChange(d -> dirty = d)
                        .onSave(bytes -> document = bytes)   // the .docx, as Word writes it""",
                DocxEditor.class);
        gallery.page("Documents", "Document side panel", () -> row(
                        new DocumentViewerSidebar("Outline")
                                .entry("title", "Quarterly Report")
                                .entry("summary", "1. Summary")
                                .entry("highlights", "1.1 Highlights", 1)
                                .entry("numbers", "2. Numbers")
                                .entry("appendix", "Appendix", 0, "Landscape pages")
                                .active(heading).onSelect(id -> heading = id),
                        text("Chosen: " + heading).tone(Tone.SECONDARY)).height(320),
                """
                new DocumentViewerSidebar("Outline")
                        .entry("summary", "1. Summary").entry("highlights", "1.1 Highlights", 1)
                        .active(heading).onSelect(id -> heading = id)""",
                DocumentViewerSidebar.class);
        gallery.page("Documents", "Word comment cards", () -> column(
                        new DocxAnnotationCard(DocxAnnotationCard.Kind.COMMENT, "Is this against the same quarter last year?")
                                .author("Jules Park").date("Nov 22, 2026").anchorText("Revenue grew by 12% over the quarter")
                                .active(card == 0).onClick(() -> card = 0).width(280),
                        new DocxAnnotationCard(DocxAnnotationCard.Kind.INSERTION, "Support tickets fell by a third.")
                                .author("Ana Reyes").date("Nov 20, 2026").active(card == 1).onClick(() -> card = 1).width(280),
                        new DocxAnnotationCard(DocxAnnotationCard.Kind.DELETION, "Costs were flat.")
                                .author("Marco Silva").active(card == 2).onClick(() -> card = 2).width(280)),
                """
                new DocxAnnotationCard(DocxAnnotationCard.Kind.COMMENT, "Is this against last year?")
                        .author("Jules Park").date("Nov 22, 2026").anchorText("Revenue grew by 12%")
                        .active(card == 0).onClick(() -> card = 0)""",
                DocxAnnotationCard.class);
        gallery.page("Documents", "Word layout blocks", () -> column(
                        new DocxLayoutBlocks().bytes(REPORT).height(460)
                                .onSelect(b -> block = b.type() + " at ¶" + (b.nodeIndex() + 1) + ": " + b.text()),
                        text(block).tone(Tone.SECONDARY).lines(2)),
                """
                new DocxLayoutBlocks().file(Path.of("report.docx"))
                        .onSelect(b -> chosen = b.id())   // b.type(), b.text(), b.nodeIndex()""",
                DocxLayoutBlocks.class);
    }

    private static byte[] resource(String name) {
        try (InputStream in = DocxDemos.class.getResourceAsStream(name)) {
            if (in == null) throw new IllegalStateException("the Sampler has no " + name);
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
