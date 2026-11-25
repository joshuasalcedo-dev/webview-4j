package io.joshuasalcedo.webview4j.fx;

/** A size in bytes as people say it: 512 B, 1.5 KB, 2.0 MB. */
final class Bytes {

    private Bytes() {}

    static String of(long size) {
        if (size <= 0) return "";
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        return String.format("%.1f MB", size / (1024.0 * 1024));
    }
}
