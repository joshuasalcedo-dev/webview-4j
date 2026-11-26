package io.joshuasalcedo.webview4j.plugin.document;

import io.joshuasalcedo.webview4j.ui.Component;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A PDF viewer: the pages scroll, zoom (in, out, fit), page n of N, search, text selection (copied with Ctrl+C /
 * Cmd+C), page thumbnails at the side, rotate, download. The Java side of the kit's {@code PdfViewer}; PDFium draws
 * it in the page (WebAssembly, loaded the first time a PDF is shown), so Java only gives the file.
 * <pre>{@code
 * PdfViewer.of(Path.of("statement.pdf")).zoom(PdfViewer.Zoom.FIT_WIDTH).thumbnails()
 *         .page(page).onPageChange(n -> page = n).onLoad(n -> pages = n)
 * }</pre>
 * A file or bytes go to the page as a data: URL, so a very large PDF is better given as a URL the app serves.
 */
public final class PdfViewer extends Component<PdfViewer> {

    /** How the pages are sized, besides a scale ({@link #zoom(double)}). */
    public enum Zoom {
        /** The width, at most 100% (the default). */
        AUTOMATIC("automatic"),
        /** The page's width fills the viewer. */
        FIT_WIDTH("fit-width"),
        /** A whole page fits in the viewer. */
        FIT_PAGE("fit-page");

        final String key;

        Zoom(String key) {
            this.key = key;
        }
    }

    /** A PDF the person opened (Open, in the toolbar) or downloaded (Download, with {@link #onDownload}). */
    public record PdfFile(String name, byte[] bytes) {}

    private PdfViewer() {
        super("pdfViewer");
    }

    /** The PDF at a URL (http(s), or one the app serves); also a data: URL, or the bytes as base64. */
    public static PdfViewer src(String url) {
        return new PdfViewer().prop("src", url);
    }

    /** A PDF file; its name is the file's. */
    public static PdfViewer of(Path file) {
        return of(PdfSource.read(file)).fileName(file.getFileName().toString());
    }

    /** A PDF's bytes. */
    public static PdfViewer of(byte[] bytes) {
        return new PdfViewer().prop("src", PdfSource.dataUrl(bytes));
    }

    /** No PDF yet: the viewer asks for one to be opened (Open is on). */
    public static PdfViewer empty() {
        return new PdfViewer().prop("upload", true);
    }

    /** Its name, for Download and screen readers. */
    public PdfViewer fileName(String name) { return prop("fileName", name); }

    /** What it shows, for screen readers (the file's name if left out). */
    public PdfViewer label(String label) { return prop("label", label); }

    public PdfViewer zoom(Zoom zoom) { return prop("zoom", zoom.key); }

    /** A scale: 1 is 100%. */
    public PdfViewer zoom(double scale) { return prop("zoom", scale); }

    /** Scrolls to this page (1 for the first) each time it changes. */
    public PdfViewer page(int page) { return prop("page", page); }

    /** The page thumbnails at the side, open at first. */
    public PdfViewer thumbnails() { return prop("thumbnails", true); }

    /** Searched for as soon as the PDF is open: the matches are marked, the first is scrolled to. */
    public PdfViewer search(String text) { return prop("search", text); }

    /** The toolbar (on unless false). */
    public PdfViewer toolbar(boolean shown) { return prop("toolbar", shown); }

    /** The Download button (on unless false). */
    public PdfViewer download(boolean shown) { return prop("download", shown); }

    /** The rotate buttons (on unless false). */
    public PdfViewer rotate(boolean shown) { return prop("rotate", shown); }

    /** An Open button: the person picks a PDF and it is shown ({@link #onOpen} gets it). Off unless set. */
    public PdfViewer upload(boolean shown) { return prop("upload", shown); }

    /** The page mostly in view changed (1 for the first). */
    public PdfViewer onPageChange(Consumer<Integer> action) { return on("onPageChange", v -> action.accept(PdfSource.whole(v))); }

    /** The PDF is open: how many pages it has. */
    public PdfViewer onLoad(Consumer<Integer> pages) { return on("onLoad", v -> pages.accept(PdfSource.whole(v))); }

    /** The PDF could not be read or opened: why. */
    public PdfViewer onError(Consumer<String> action) { return on("onError", v -> action.accept(String.valueOf(v))); }

    /** A PDF the person opened with Open (it is shown too). Turns Open on. */
    public PdfViewer onOpen(Consumer<PdfFile> action) {
        prop("upload", true);
        return on("onOpen", v -> action.accept(file(v)));
    }

    /** Download gives Java the PDF (turned as shown) instead of the browser's download, e.g. to save it with a dialog. */
    public PdfViewer onDownload(Consumer<PdfFile> action) { return on("onDownload", v -> action.accept(file(v))); }

    private static PdfFile file(Object value) {
        Map<?, ?> m = value instanceof Map<?, ?> map ? map : Map.of();
        return new PdfFile(String.valueOf(m.get("name")), PdfSource.decode(m.get("data")));
    }
}
