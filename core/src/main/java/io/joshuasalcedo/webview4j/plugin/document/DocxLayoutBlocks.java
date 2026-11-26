package io.joshuasalcedo.webview4j.plugin.document;

import io.joshuasalcedo.webview4j.ui.Component;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A Word document's layout blocks, as a layout reader (OCR) lists them: its title, headings, paragraphs, list items,
 * tables, pictures, header and footer, each a card with a coloured badge; pills on top show and hide each kind. The
 * Java side of the kit's {@code DocxLayoutBlocks}.
 * <pre>{@code
 * new DocxLayoutBlocks().file(Path.of("report.docx")).active(chosen).onSelect(b -> chosen = b.id())
 * }</pre>
 */
public final class DocxLayoutBlocks extends Component<DocxLayoutBlocks> {

    /**
     * A block: its id, its kind (title, heading, paragraph, list, table, figure, header, footer), its text, where it
     * is in the body (-1 for a header or footer) and its level (a heading's 1 to 6, a list item's depth from 0).
     */
    public record Block(String id, String type, String text, int nodeIndex, int level) {
        static Block of(Object value) {
            Map<?, ?> m = value instanceof Map<?, ?> map ? map : Map.of();
            return new Block(DocxBytes.text(m.get("id")), DocxBytes.text(m.get("type")), DocxBytes.text(m.get("text")),
                    m.get("nodeIndex") instanceof Number n ? n.intValue() : -1, DocxBytes.whole(m.get("level")));
        }
    }

    public DocxLayoutBlocks() {
        super("docxLayoutBlocks");
    }

    /** The document at a URL the page can read. */
    public DocxLayoutBlocks src(String url) { return prop("src", url); }

    /** The document's bytes (a .docx). */
    public DocxLayoutBlocks bytes(byte[] docx) { return prop("src", DocxBytes.dataUrl(docx)); }

    /**
     * The document in a file, read now.
     *
     * @throws java.io.UncheckedIOException when the file can't be read
     */
    public DocxLayoutBlocks file(Path file) { return bytes(DocxBytes.read(file)); }

    /** What screen readers call it. */
    public DocxLayoutBlocks label(String label) { return prop("label", label); }

    /** The id of the block chosen. */
    public DocxLayoutBlocks active(String id) { return prop("active", id); }

    /** The pills that show and hide each kind (on unless false). */
    public DocxLayoutBlocks filters(boolean shown) { return prop("showFilters", shown); }

    /** A block was clicked. */
    public DocxLayoutBlocks onSelect(Consumer<Block> action) { return on("onSelect", v -> action.accept(Block.of(v))); }

    /** The pointer or the focus came onto a block (each one hovered, so often). */
    public DocxLayoutBlocks onFocus(Consumer<Block> action) { return on("onFocus", v -> action.accept(Block.of(v))); }

    /** The document could not be read: what went wrong. */
    public DocxLayoutBlocks onError(Consumer<String> action) { return on("onError", v -> action.accept(DocxBytes.text(v))); }
}
