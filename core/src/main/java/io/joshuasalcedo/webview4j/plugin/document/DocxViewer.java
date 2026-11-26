package io.joshuasalcedo.webview4j.plugin.document;

/**
 * A Word document (.docx), read-only, as Word shows it: its pages, page thumbnails, the outline and the sections in a
 * side panel, comments and tracked changes in the margin and as a list, find, zoom (a percentage, fit width, whole
 * page), Download and Open. Dark pages in the dark theme. The Java side of the kit's {@code DocxViewer}.
 * <pre>{@code
 * new DocxViewer().file(Path.of("report.docx")).fitPage().panel(DocxViewer.Panel.OUTLINE).grow()
 * new DocxViewer().bytes(docx).fileName("Report.docx").onDownload((name, bytes) -> save(name, bytes))
 * }</pre>
 */
public final class DocxViewer extends DocxDocument<DocxViewer> {

    public DocxViewer() {
        super("docxViewer");
    }

    /** The pages drawn dark, the text light, pictures in their colours (left out: dark in the dark theme). */
    public DocxViewer darkPages(boolean dark) { return prop("darkPages", dark); }
}
