package io.joshuasalcedo.swaswa.ffi;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What a native open, save or folder dialog shows. Show it with {@link App#openFile},
 * {@link App#openFiles}, {@link App#saveFile} or {@link App#pickFolder}.
 * <pre>{@code
 * Optional<Path> image = app.openFile(new FileDialog().title("Choose a picture")
 *         .filter("Images", "png", "jpg", "gif").parent(view)).join();
 * Optional<Path> target = app.saveFile(new FileDialog().fileName("report.pdf").filter("PDF", "pdf")).join();
 * }</pre>
 *
 * <p>The dialog never holds the app up: its future completes when the user chooses or cancels,
 * and the app's other windows keep working meanwhile. On Linux it goes through the desktop's file
 * chooser portal (XDG desktop portal), as sandboxed apps do.
 */
public final class FileDialog {

    private String title;
    private Path directory;
    private String fileName;
    private final List<Map<String, Object>> filters = new ArrayList<>();
    private View parent;

    /** The dialog's title; the system's own unless set. */
    public FileDialog title(String title) {
        this.title = Objects.requireNonNull(title, "title");
        return this;
    }

    /** The directory it opens in. */
    public FileDialog directory(Path directory) {
        this.directory = Objects.requireNonNull(directory, "directory").toAbsolutePath();
        return this;
    }

    /** The name it suggests: for a save dialog. */
    public FileDialog fileName(String fileName) {
        this.fileName = Objects.requireNonNull(fileName, "fileName");
        return this;
    }

    /**
     * Only files with these extensions, under a name such as {@code "Images"}. Call it again for
     * each choice the dialog offers; the first is chosen to begin with.
     *
     * @throws IllegalArgumentException without extensions
     */
    public FileDialog filter(String name, String... extensions) {
        Objects.requireNonNull(name, "name");
        if (extensions.length == 0) throw new IllegalArgumentException("a filter needs at least one extension");
        List<String> clean = new ArrayList<>();
        for (String extension : extensions) {
            String e = Objects.requireNonNull(extension, "extension").startsWith(".") ? extension.substring(1) : extension;
            if (e.isEmpty()) throw new IllegalArgumentException("an extension is not empty");
            clean.add(e);
        }
        filters.add(Map.of("name", name, "extensions", List.copyOf(clean)));
        return this;
    }

    /** The window it belongs to: it opens over it, modal to it. */
    public FileDialog parent(View view) {
        this.parent = Objects.requireNonNull(view, "view");
        return this;
    }

    /** The payload of {@code app.dialog.file} (PROTOCOL.md §5.7). */
    Map<String, Object> toPayload(String kind) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("kind", kind);
        if (title != null) payload.put("title", title);
        if (directory != null) payload.put("directory", directory.toString());
        if (fileName != null) payload.put("fileName", fileName);
        if (!filters.isEmpty()) payload.put("filters", List.copyOf(filters));
        if (parent != null) payload.put("view", parent.id());
        return payload;
    }
}
