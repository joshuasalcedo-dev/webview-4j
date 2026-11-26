package io.joshuasalcedo.webview4j.plugin.document;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;

/** How the Word components pass a document between Java and the page: as base64, in a data: URL going there. */
final class DocxBytes {

    static final String MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private DocxBytes() {}

    /** The bytes as a data: URL, which the page reads as it reads any URL. */
    static String dataUrl(byte[] docx) {
        return "data:" + MIME + ";base64," + Base64.getEncoder().encodeToString(docx);
    }

    /** The file's bytes; an {@link UncheckedIOException} when it can't be read. */
    static byte[] read(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException("can't read " + file, e);
        }
    }

    /** The bytes the page sent ({@code {fileName, base64}}, or base64 alone). */
    static byte[] bytes(Object value) {
        Object base64 = value instanceof Map<?, ?> map ? map.get("base64") : value;
        return base64 instanceof String s ? Base64.getDecoder().decode(s) : new byte[0];
    }

    /** The file name the page sent with the bytes ("document.docx" when none). */
    static String fileName(Object value) {
        return value instanceof Map<?, ?> map && map.get("fileName") instanceof String s ? s : "document.docx";
    }

    static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    static int whole(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }
}
