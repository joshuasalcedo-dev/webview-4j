package io.joshuasalcedo.webview4j.plugin.document;

import io.joshuasalcedo.webview4j.ui.Component;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * What {@link DocxViewer} and {@link DocxEditor} both have: the document (a URL, a file or its bytes), its name, the
 * zoom, the side panel, comments, Download and Open, and what the person does with them.
 *
 * @param <C> the component's own type, so its settings chain
 */
public abstract sealed class DocxDocument<C extends DocxDocument<C>> extends Component<C> permits DocxViewer, DocxEditor {

    /** A side panel of the viewer. */
    public enum Panel {
        /** Page thumbnails. */
        PAGES,
        /** The headings. */
        OUTLINE,
        /** The sections: each one's page size and orientation. */
        SECTIONS,
        /** The comments and tracked changes, as a list. */
        COMMENTS
    }

    DocxDocument(String type) {
        super(type);
    }

    /** The document at a URL the page can read (an app's own resource, https:, or a data: URL). */
    public C src(String url) { return prop("src", url); }

    /** The document's bytes (a .docx): sent to the page as a data: URL, so best for documents of a few MB at most. */
    public C bytes(byte[] docx) { return prop("src", DocxBytes.dataUrl(docx)); }

    /**
     * The document in a file: read now and sent as {@link #bytes bytes}; its name is the file's unless
     * {@link #fileName} says otherwise.
     *
     * @throws java.io.UncheckedIOException when the file can't be read
     */
    public C file(Path file) {
        if (get("fileName") == null && file.getFileName() != null) prop("fileName", file.getFileName().toString());
        return bytes(DocxBytes.read(file));
    }

    /** The name it is shown and saved as. */
    public C fileName(String name) { return prop("fileName", name); }

    /** What screen readers call it ("Word document" unless set). */
    public C label(String label) { return prop("label", label); }

    /** The pages at this size, a percentage (10 to 400). */
    public C zoom(int percent) { return prop("zoom", percent); }

    /** The pages as wide as the viewer (the default). */
    public C fitWidth() { return prop("zoom", "fit-width"); }

    /** A whole page in view. */
    public C fitPage() { return prop("zoom", "fit-page"); }

    /** The side panel open at first, on this. */
    public C panel(Panel panel) { return prop("panel", panel == null ? null : panel.name().toLowerCase(Locale.ROOT)); }

    /** The toolbar (on unless false). */
    public C toolbar(boolean shown) { return prop("showToolbar", shown); }

    /** Comments in the page's margin at first (on unless false). */
    public C comments(boolean shown) { return prop("showComments", shown); }

    /** Tracked changes marked in the text at first (the viewer: on; the editor: off, as it is read-only while shown). */
    public C trackedChanges(boolean shown) { return prop("showTrackedChanges", shown); }

    /** A Download item (on unless false). */
    public C download(boolean shown) { return prop("showDownload", shown); }

    /** An Open item, to show another .docx (on unless false). */
    public C upload(boolean shown) { return prop("showUpload", shown); }

    /** Download gives Java the file's name and bytes instead of saving it (Java saves it where it wants). */
    public C onDownload(BiConsumer<String, byte[]> action) {
        return on("onDownload", v -> action.accept(DocxBytes.fileName(v), DocxBytes.bytes(v)));
    }

    /** A .docx was opened with Open (it is shown either way): its name and bytes. */
    public C onUpload(BiConsumer<String, byte[]> action) {
        return on("onUpload", v -> action.accept(DocxBytes.fileName(v), DocxBytes.bytes(v)));
    }

    /** The page in view changed (1 first). */
    public C onPageChange(Consumer<Integer> action) { return on("onPageChange", v -> action.accept(DocxBytes.whole(v))); }

    /** The person zoomed: a percentage ("150"), or "fit-width" or "fit-page". */
    public C onZoomChange(Consumer<String> action) {
        return on("onZoomChange", v -> action.accept(v instanceof Number n ? String.valueOf(n.intValue()) : DocxBytes.text(v)));
    }

    /** The document could not be shown: what went wrong. */
    public C onError(Consumer<String> action) { return on("onError", v -> action.accept(DocxBytes.text(v))); }
}
