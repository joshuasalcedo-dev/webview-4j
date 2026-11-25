package io.joshuasalcedo.webview4j.ui.components.usecase.xlsx;

import io.joshuasalcedo.webview4j.ui.Component;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Objects;

/**
 * What the spreadsheet views share: the file they show (a URL, a file on disk, or its bytes), its name, and their
 * toolbar. A file or bytes reach the page inside the screen's tree, as a data: URL: fine for a document, not for
 * hundreds of megabytes (the page opens 50 MB at most).
 */
public abstract class SheetFile<C extends SheetFile<C>> extends Component<C> {

    private final String mimeType;

    protected SheetFile(String type, String mimeType) {
        super(type);
        this.mimeType = mimeType;
    }

    /** Where the file is: a URL the page can fetch (http(s), the app's own pages, a data: URL). */
    public C src(String url) {
        return prop("src", url);
    }

    /** The file on disk; its name is the file's, unless {@link #fileName} says otherwise. */
    public C file(Path file) {
        Objects.requireNonNull(file, "file");
        try {
            bytes(Files.readAllBytes(file));
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + file, e);
        }
        if (get("fileName") == null && file.getFileName() != null) fileName(file.getFileName().toString());
        return self();
    }

    /** The file's bytes. */
    public C bytes(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        return prop("src", "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(bytes));
    }

    /** The name it is shown and downloaded as. */
    public C fileName(String fileName) {
        return prop("fileName", fileName);
    }

    /** The bar of zoom, search and download (shown unless false). */
    public C showToolbar(boolean show) {
        return prop("showToolbar", show);
    }

    /** Bytes the page sent as base64; none for anything else. */
    static byte[] decode(Object value) {
        return value instanceof String s && !s.isEmpty() ? Base64.getDecoder().decode(s) : new byte[0];
    }
}
