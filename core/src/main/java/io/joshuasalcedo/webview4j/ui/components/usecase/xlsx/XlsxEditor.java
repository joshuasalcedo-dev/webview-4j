package io.joshuasalcedo.webview4j.ui.components.usecase.xlsx;

import java.util.function.Consumer;

/**
 * An Excel workbook to edit: cells and formulas (the formula bar), bold and italic, merging, sheets added and
 * deleted, undo and redo, copy and paste, a right-click menu. Save (its button, or Ctrl+S / ⌘S) sends the workbook
 * to {@link #onSave} as .xlsx bytes.
 * <pre>{@code
 * new XlsxEditor().file(path).grow()
 *         .onChange(() -> edited = true)
 *         .onSave(bytes -> Files.write(path, bytes))
 * }</pre>
 * Giving it other bytes (or another file) opens them afresh: what was being edited, and its undo, is gone.
 */
public final class XlsxEditor extends SheetFile<XlsxEditor> {

    public XlsxEditor() {
        super("xlsxEditor", XlsxViewer.XLSX);
    }

    /** Shows it without letting it be edited. */
    public XlsxEditor readOnly(boolean readOnly) {
        return prop("readOnly", readOnly);
    }

    /** Save: the workbook as .xlsx bytes. Without it, the toolbar only offers a download of the edited copy. */
    public XlsxEditor onSave(Consumer<byte[]> action) {
        return on("onSave", v -> action.accept(decode(v)));
    }

    /** The workbook was edited (a cell, a style, a sheet): each change. */
    public XlsxEditor onChange(Runnable action) {
        return on("onChange", v -> action.run());
    }
}
