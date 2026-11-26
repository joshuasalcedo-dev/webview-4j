package io.joshuasalcedo.webview4j.plugin.document;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * A Word document (.docx) to edit, as in Word: typing on the pages, undo and redo, paragraph styles, fonts and sizes,
 * bold, italic, underline, strikethrough, colours and highlight, alignment, lists, line spacing, borders, links,
 * tables, pictures, a read-only lock; New, Open, Save (Ctrl+S, ⌘S on a Mac) and Download. Everything
 * {@link DocxViewer} has too. Save gives Java the .docx bytes. The Java side of the kit's {@code DocxEditor}.
 * <pre>{@code
 * new DocxEditor().bytes(document).fileName("Letter.docx")
 *         .onSave(bytes -> document = bytes)
 *         .onDirtyChange(d -> edited = d)
 * }</pre>
 * Giving it back the bytes it saved (as {@code bytes(document)} does on the next render) does not open the document
 * again: the person goes on where they were.
 */
public final class DocxEditor extends DocxDocument<DocxEditor> {

    public DocxEditor() {
        super("docxEditor");
    }

    /** Nothing can be changed (the toolbar's lock switches it, unless {@link #readOnlyToggle} is off). */
    public DocxEditor readOnly(boolean readOnly) { return prop("readOnly", readOnly); }

    /** The lock in the toolbar (on unless false). */
    public DocxEditor readOnlyToggle(boolean shown) { return prop("showReadOnlyToggle", shown); }

    /** The lock was switched. */
    public DocxEditor onReadOnlyChange(Consumer<Boolean> action) {
        return on("onReadOnlyChange", v -> action.accept(Boolean.TRUE.equals(v)));
    }

    /** Save: the document as .docx bytes. Without it, Save downloads the file. */
    public DocxEditor onSave(Consumer<byte[]> action) { return on("onSave", v -> action.accept(DocxBytes.bytes(v))); }

    /** Save: the file's name and the document as .docx bytes. */
    public DocxEditor onSave(BiConsumer<String, byte[]> action) {
        return on("onSave", v -> action.accept(DocxBytes.fileName(v), DocxBytes.bytes(v)));
    }

    /** The document was changed (a moment after the typing stops); Save gives the bytes. */
    public DocxEditor onChange(Runnable action) { return on("onChange", v -> action.run()); }

    /** Whether it has changes not saved yet, each time that flips. */
    public DocxEditor onDirtyChange(Consumer<Boolean> action) {
        return on("onDirtyChange", v -> action.accept(Boolean.TRUE.equals(v)));
    }
}
