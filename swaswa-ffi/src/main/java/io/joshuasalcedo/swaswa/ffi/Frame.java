package io.joshuasalcedo.swaswa.ffi;

import java.util.Objects;

/**
 * A picture for one of a page's canvases ({@link View#drawFrame(String, Frame)}): raw RGBA pixels, 4 bytes each,
 * row by row from the top (what a renderer draws into), or an encoded PNG or JPEG (smaller to send: a photo, a
 * chart drawn once). The bytes go to the page as they are, binary: no JSON, no base64.
 */
public record Frame(int width, int height, Format format, byte[] bytes) {

    public enum Format { RGBA, PNG, JPEG }

    public Frame {
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(bytes, "bytes");
        if (format == Format.RGBA) {
            if (width <= 0 || height <= 0) throw new IllegalArgumentException("RGBA pixels have a size: " + width + " × " + height);
            if ((long) width * height * 4 != bytes.length) {
                throw new IllegalArgumentException("RGBA pixels are width × height × 4 bytes: " + width + " × " + height + " × 4 is not " + bytes.length);
            }
        }
    }

    /** {@code width × height} pixels, 4 bytes each (red, green, blue, alpha), row by row from the top. */
    public static Frame rgba(int width, int height, byte[] pixels) {
        return new Frame(width, height, Format.RGBA, pixels);
    }

    /** A PNG, drawn at its own size. */
    public static Frame png(byte[] png) {
        if (png.length < 8 || (png[0] & 0xFF) != 0x89 || png[1] != 'P' || png[2] != 'N' || png[3] != 'G') {
            throw new IllegalArgumentException("not a PNG");
        }
        return new Frame(0, 0, Format.PNG, png);
    }

    /** A JPEG, drawn at its own size. */
    public static Frame jpeg(byte[] jpeg) {
        if (jpeg.length < 3 || (jpeg[0] & 0xFF) != 0xFF || (jpeg[1] & 0xFF) != 0xD8) throw new IllegalArgumentException("not a JPEG");
        return new Frame(0, 0, Format.JPEG, jpeg);
    }
}
