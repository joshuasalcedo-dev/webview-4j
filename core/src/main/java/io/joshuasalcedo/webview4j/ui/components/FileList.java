package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** A folder's files as a list with their size and date. */
public final class FileList extends Component<FileList> {

    public FileList(String label, java.util.List<FileNode> items) {
        super("fileList");
        prop("label", label);
        prop("items", FileNode.json(items));
    }

    /** The paths chosen; several with Ctrl/⌘ or Shift. */
    public FileList selected(java.util.List<String> paths) { return prop("selected", java.util.List.copyOf(paths)); }
    /** A file opened (double-click, Enter): its path. */
    public FileList onOpen(java.util.function.Consumer<String> action) { return on("onOpen", v -> action.accept(Keys.text(v))); }
    public FileList onSelectionChange(java.util.function.Consumer<java.util.List<String>> action) { return on("onSelectionChange", v -> action.accept(Keys.texts(v))); }
    /** Rename in its right-click menu (and F2): the path. */
    public FileList onRename(java.util.function.Consumer<String> action) { return on("onRename", v -> action.accept(Keys.text(v))); }
    /** Delete in its right-click menu (and the Delete key): the path. */
    public FileList onDelete(java.util.function.Consumer<String> action) { return on("onDelete", v -> action.accept(Keys.text(v))); }
}
