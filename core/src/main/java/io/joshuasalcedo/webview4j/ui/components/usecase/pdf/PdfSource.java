package io.joshuasalcedo.webview4j.ui.components.usecase.pdf;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

/** How a file's bytes go to the page: a data: URL of their base64 (the page reads it as it reads a URL). */
final class PdfSource {

    private PdfSource() {}

    static String dataUrl(byte[] bytes) {
        return dataUrl(bytes, "application/pdf");
    }

    static String dataUrl(byte[] bytes, String mediaType) {
        return "data:" + mediaType + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }

    static byte[] read(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException("the file could not be read: " + file, e);
        }
    }

    /** The bytes the page sent as base64 (a file opened, a PDF downloaded). */
    static byte[] decode(Object base64) {
        return base64 == null ? new byte[0] : Base64.getDecoder().decode(String.valueOf(base64));
    }

    /** A whole number the page sent; 0 for none. */
    static int whole(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }
}
