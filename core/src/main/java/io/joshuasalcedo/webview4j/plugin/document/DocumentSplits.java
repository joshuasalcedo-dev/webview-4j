package io.joshuasalcedo.webview4j.plugin.document;

import io.joshuasalcedo.webview4j.ui.Component;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * The documents a PDF is split into, each a card of its pages: pages are dragged between and within splits, splits
 * are dragged to reorder them (Alt+arrow keys, and each page's right-click menu, do the same). The Java side of the
 * kit's {@code DocumentSplits}; pair it with a {@link PdfViewer} that {@link #onSelectPage} scrolls.
 * <pre>{@code
 * DocumentSplits.of(Path.of("scan.pdf"), splits).onChange(s -> splits = s).onSelectPage(n -> page = n)
 * }</pre>
 */
public final class DocumentSplits extends Component<DocumentSplits> {

    /** One document: its id, its name and its pages (1 for the first), in their order. */
    public record Split(String id, String title, List<Integer> pages) {

        public Split {
            Objects.requireNonNull(id, "id");
            pages = List.copyOf(pages);
        }
    }

    private DocumentSplits(String src, List<Split> splits) {
        super("documentSplits");
        prop("src", src);
        prop("splits", List.copyOf(splits));
    }

    /** The PDF at a URL (its pages' thumbnails are drawn from it), and its splits. */
    public static DocumentSplits src(String url, List<Split> splits) {
        return new DocumentSplits(url, splits);
    }

    public static DocumentSplits of(Path pdf, List<Split> splits) {
        return new DocumentSplits(PdfSource.dataUrl(PdfSource.read(pdf)), splits);
    }

    public static DocumentSplits of(byte[] pdf, List<Split> splits) {
        return new DocumentSplits(PdfSource.dataUrl(pdf), splits);
    }

    /** {@code parts} splits of about as many pages each: "Pages 1-3", "Pages 4-6", ... */
    public static List<Split> evenly(int pageCount, int parts) {
        if (pageCount <= 0) return List.of(new Split("split-1", "Split 1", List.of()));
        List<Split> splits = new ArrayList<>();
        int size = Math.max(1, (pageCount + Math.max(1, parts) - 1) / Math.max(1, parts));
        for (int first = 1; first <= pageCount; first += size) {
            int last = Math.min(pageCount, first + size - 1);
            List<Integer> pages = new ArrayList<>();
            for (int p = first; p <= last; p++) pages.add(p);
            splits.add(new Split("split-" + (splits.size() + 1), first == last ? "Page " + first : "Pages " + first + "-" + last, pages));
        }
        return List.copyOf(splits);
    }

    public DocumentSplits label(String label) { return prop("label", label); }

    /** The Add split button (on unless false). */
    public DocumentSplits canAdd(boolean shown) { return prop("canAdd", shown); }

    /** The splits' names can be changed (on unless false). */
    public DocumentSplits editableTitles(boolean editable) { return prop("editableTitles", editable); }

    /** The splits changed: a page moved, a split was moved, added, removed or renamed. */
    public DocumentSplits onChange(Consumer<List<Split>> action) { return on("onChange", v -> action.accept(splits(v))); }

    /** A page was clicked (1 for the first). */
    public DocumentSplits onSelectPage(Consumer<Integer> action) { return on("onSelectPage", v -> action.accept(PdfSource.whole(v))); }

    private static List<Split> splits(Object value) {
        List<Split> out = new ArrayList<>();
        if (!(value instanceof List<?> items)) return out;
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> m)) continue;
            List<Integer> pages = new ArrayList<>();
            if (m.get("pages") instanceof List<?> ps) for (Object p : ps) pages.add(PdfSource.whole(p));
            out.add(new Split(String.valueOf(m.get("id")), m.get("title") == null ? "" : String.valueOf(m.get("title")), pages));
        }
        return List.copyOf(out);
    }
}
