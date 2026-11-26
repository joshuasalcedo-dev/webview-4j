package io.joshuasalcedo.webview4j.plugin.document;

import io.joshuasalcedo.webview4j.ui.Component;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A document viewer's side panel: a list of entries (an outline's headings, sections, pages), the chosen one marked,
 * and any components; beside the page, or floating over it. The kit's sidebar built from parts, so it follows the
 * platform and the theme. The Java side of the kit's {@code DocumentViewerSidebar}.
 * <pre>{@code
 * new DocumentViewerSidebar("Outline")
 *         .entry("intro", "Introduction").entry("scope", "Scope", 1)
 *         .active(chosen).onSelect(id -> chosen = id)
 * }</pre>
 */
public final class DocumentViewerSidebar extends Component<DocumentViewerSidebar> {

    private final List<Map<String, Object>> entries = new ArrayList<>();

    /** {@code title}: its heading, and what screen readers call it. */
    public DocumentViewerSidebar(String title) {
        super("documentViewerSidebar");
        prop("title", title);
        prop("label", title);
        prop("entries", entries);
    }

    /** An entry: its id (what {@link #onSelect} gets) and its label. */
    public DocumentViewerSidebar entry(String id, String label) { return entry(id, label, 0, null); }

    /** An entry indented {@code level} steps (an outline's sub-headings). */
    public DocumentViewerSidebar entry(String id, String label, int level) { return entry(id, label, level, null); }

    /** An entry with a second, quieter line. */
    public DocumentViewerSidebar entry(String id, String label, int level, String description) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", id);
        entry.put("label", label);
        if (level > 0) entry.put("level", level);
        if (description != null) entry.put("description", description);
        entries.add(entry);
        return this;
    }

    /** Components under the entries (or instead of them). */
    public DocumentViewerSidebar content(Component<?>... components) { return add(components); }

    /** The id of the entry chosen. */
    public DocumentViewerSidebar active(String id) { return prop("active", id); }

    /** Shown (true unless set) or folded away. */
    public DocumentViewerSidebar open(boolean open) { return prop("open", open); }

    /** Beside what follows (true unless set), or floating over it. */
    public DocumentViewerSidebar inline(boolean inline) { return prop("inline", inline); }

    /** On the right instead of the left. */
    public DocumentViewerSidebar right() { return prop("side", "right"); }

    /** What it says when it has no entries. */
    public DocumentViewerSidebar empty(String text) { return prop("empty", text); }

    /** An entry was chosen: its id. */
    public DocumentViewerSidebar onSelect(Consumer<String> action) {
        return on("onSelect", v -> action.accept(DocxBytes.text(v)));
    }
}
