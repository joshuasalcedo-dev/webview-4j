package io.joshuasalcedo.swaswa.ffi;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The apps' own files, served to their windows over the custom protocol ({@link WindowOptions#app}).
 * A window's files are a class path directory; the native side asks for one by that directory and
 * a path, and only directories Java opened windows with are served from. Paths are checked here as
 * well as natively: letters, digits and {@code ._-~@+}, no empty or dot-led segments.
 */
final class Assets {

    private static final Map<String, String> TYPES = Map.ofEntries(
            Map.entry("html", "text/html; charset=utf-8"),
            Map.entry("htm", "text/html; charset=utf-8"),
            Map.entry("js", "text/javascript; charset=utf-8"),
            Map.entry("mjs", "text/javascript; charset=utf-8"),
            Map.entry("css", "text/css; charset=utf-8"),
            Map.entry("json", "application/json"),
            Map.entry("map", "application/json"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"),
            Map.entry("ico", "image/x-icon"),
            Map.entry("woff", "font/woff"),
            Map.entry("woff2", "font/woff2"),
            Map.entry("ttf", "font/ttf"),
            Map.entry("otf", "font/otf"),
            Map.entry("avif", "image/avif"),
            Map.entry("bmp", "image/bmp"),
            Map.entry("wasm", "application/wasm"),
            Map.entry("txt", "text/plain; charset=utf-8"),
            Map.entry("md", "text/markdown; charset=utf-8"),
            Map.entry("csv", "text/csv; charset=utf-8"),
            Map.entry("xml", "application/xml"),
            Map.entry("pdf", "application/pdf"),
            // Sound and video: a player plays them, and seeks with range requests (the native side answers them).
            Map.entry("mp3", "audio/mpeg"),
            Map.entry("m4a", "audio/mp4"),
            Map.entry("aac", "audio/aac"),
            Map.entry("ogg", "audio/ogg"),
            Map.entry("oga", "audio/ogg"),
            Map.entry("opus", "audio/ogg"),
            Map.entry("wav", "audio/wav"),
            Map.entry("flac", "audio/flac"),
            Map.entry("weba", "audio/webm"),
            Map.entry("mp4", "video/mp4"),
            Map.entry("m4v", "video/mp4"),
            Map.entry("webm", "video/webm"),
            Map.entry("ogv", "video/ogg"),
            Map.entry("mov", "video/quicktime"),
            // Captions and subtitles for <track>.
            Map.entry("vtt", "text/vtt; charset=utf-8"));

    /**
     * The roots the native side asks for, each with the class path directory that holds its files and the loader
     * that reads them: a window's own (its directory, as given), and {@link #SHARED} (any directory).
     */
    private final Map<String, Source> roots = new ConcurrentHashMap<>();

    /** Where a root's files are: a class path directory, and the loader that reads it. */
    private record Source(String directory, ClassLoader loader) {}

    /**
     * The root the native side asks for when a page loads {@code <base>ui/...} (bridge.rs's UI_ROOT): the files any
     * page in any window may load, from whatever directory {@link #serveShared} names.
     */
    static final String SHARED = "webview4j-ui";

    /** A file to send: its type and bytes. */
    record File(String contentType, byte[] bytes) {}

    /** Serves {@code root}'s files, read by {@code loader}, from now on. */
    void serve(String root, ClassLoader loader) {
        put(root, new Source(root, loader));
    }

    /** Serves {@code directory}'s files, read by {@code loader}, as the shared ones ({@code <base>ui/...}), from now on. */
    void serveShared(String directory, ClassLoader loader) {
        put(SHARED, new Source(root(directory), loader));
    }

    private void put(String root, Source source) {
        Source known = roots.putIfAbsent(root, source);
        if (known != null && !known.equals(source)) {
            throw new IllegalStateException("the files of '" + root + "' are already served from " + known.directory()
                    + (known.loader() == source.loader() ? "" : " by another class loader"));
        }
    }

    /**
     * The file at {@code path} under {@code root}.
     *
     * @throws HttpException 404 for a directory never served or a file it lacks, 400 for a bad path
     */
    File read(String root, String path) {
        Source source = root == null ? null : roots.get(root);
        if (source == null) throw new HttpException(404, "no files are served from '" + root + "'");
        if (path == null || !safe(path)) throw new HttpException(400, "not a file path");
        try (InputStream in = source.loader().getResourceAsStream(source.directory() + "/" + path)) {
            if (in == null) throw new HttpException(404, "no file " + path);
            return new File(type(path), in.readAllBytes());
        } catch (IOException e) {
            throw new HttpException(500, "could not read " + path + ": " + e.getMessage());
        }
    }

    /** A class path directory as a root: its segments safe, no leading or trailing slash. */
    static String root(String directory) {
        String root = directory == null ? "" : directory.strip();
        while (root.startsWith("/")) root = root.substring(1);
        while (root.endsWith("/")) root = root.substring(0, root.length() - 1);
        if (root.isEmpty() || !safe(root)) throw new IllegalArgumentException("not a class path directory: " + directory);
        return root;
    }

    /** Whether {@code path} stays inside a root: the same rule the native side applies. */
    static boolean safe(String path) {
        if (path.isEmpty() || path.length() > 512) return false;
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || segment.startsWith(".")) return false;
            for (int i = 0; i < segment.length(); i++) {
                char c = segment.charAt(i);
                boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || "._-~@+".indexOf(c) >= 0;
                if (!ok) return false;
            }
        }
        return true;
    }

    /** The content type of a file, by its extension. */
    static String type(String path) {
        int dot = path.lastIndexOf('.');
        String extension = dot < 0 ? "" : path.substring(dot + 1).toLowerCase(Locale.ROOT);
        return TYPES.getOrDefault(extension, "application/octet-stream");
    }

    /** Where the custom protocol is reached from a page; the native side's {@code bridge::base_url}. */
    static String baseUrl() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.startsWith("windows") ? "https://webview4j.localhost/" : "webview4j://localhost/";
    }
}
