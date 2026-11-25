package io.joshuasalcedo.webview4j.ui.components.usecase.pdf;

import io.joshuasalcedo.webview4j.ui.Component;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * A file's thumbnail: a PDF's page (the first, unless set) drawn by the PDF engine, or an image; the file's
 * extension while there is no picture. The Java side of the kit's {@code PdfThumbnail} and {@code FileThumbnail}.
 * <pre>{@code
 * FileThumbnail.of(Path.of("statement.pdf")).width(120)
 * FileThumbnail.src("https://example.com/photo.jpg", "photo.jpg").aspectRatio(4 / 3.0).cover()
 * }</pre>
 */
public final class FileThumbnail extends Component<FileThumbnail> {

    private FileThumbnail() {
        super("fileThumbnail");
    }

    /** A file at a URL; its name tells a PDF from an image ("report.pdf"). */
    public static FileThumbnail src(String url, String fileName) {
        return new FileThumbnail().prop("src", url).prop("fileName", fileName);
    }

    /** A file: a PDF (.pdf), or an image (.png, .jpg, .gif, .webp, .svg); any other shows its extension. */
    public static FileThumbnail of(Path file) {
        return of(PdfSource.read(file), file.getFileName().toString());
    }

    /** A file's bytes and its name. */
    public static FileThumbnail of(byte[] bytes, String fileName) {
        String type = mediaType(fileName);
        FileThumbnail t = new FileThumbnail().prop("fileName", fileName);
        return type == null ? t : t.prop("src", PdfSource.dataUrl(bytes, type));
    }

    /** Which page of a PDF (1, the first, unless set). */
    public FileThumbnail page(int page) { return prop("page", page); }

    /** Width over height of the box (a PDF's is its page's, an image's 1). */
    public FileThumbnail aspectRatio(double ratio) { return prop("aspectRatio", ratio); }

    /** The picture fills the box, its edges cut (else all of it is shown). */
    public FileThumbnail cover() { return prop("fit", "cover"); }

    /** A PDF is open: how many pages it has. */
    public FileThumbnail onLoad(Consumer<Integer> pages) { return on("onLoad", v -> pages.accept(PdfSource.whole(v))); }

    private static String mediaType(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.endsWith(".pdf")) return "application/pdf";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".gif")) return "image/gif";
        if (n.endsWith(".webp")) return "image/webp";
        if (n.endsWith(".svg")) return "image/svg+xml";
        return null;
    }
}
