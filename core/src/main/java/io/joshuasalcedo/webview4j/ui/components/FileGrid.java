package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** A folder's files as icons in a grid. */
public final class FileGrid extends Component<FileGrid> {

    public FileGrid(String label, java.util.List<FileNode> items) {
        super("fileGrid");
        prop("label", label);
        prop("items", FileNode.json(items));
    }

    /** The paths chosen; several with Ctrl/⌘ or Shift. */
    public FileGrid selected(java.util.List<String> paths) { return prop("selected", java.util.List.copyOf(paths)); }
    /** A file opened (double-click, Enter): its path. */
    public FileGrid onOpen(java.util.function.Consumer<String> action) { return on("onOpen", v -> action.accept(Keys.text(v))); }
    public FileGrid onSelectionChange(java.util.function.Consumer<java.util.List<String>> action) { return on("onSelectionChange", v -> action.accept(Keys.texts(v))); }
    /** Rename in its right-click menu (and F2): the path. */
    public FileGrid onRename(java.util.function.Consumer<String> action) { return on("onRename", v -> action.accept(Keys.text(v))); }
    /** Delete in its right-click menu (and the Delete key): the path. */
    public FileGrid onDelete(java.util.function.Consumer<String> action) { return on("onDelete", v -> action.accept(Keys.text(v))); }
}
