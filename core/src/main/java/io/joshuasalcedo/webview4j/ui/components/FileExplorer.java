package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** A file explorer: a tree of folders beside a folder's contents, as a list or a grid, with its path above. */
public final class FileExplorer extends Component<FileExplorer> {

    public FileExplorer(String label, java.util.List<FileNode> items) {
        super("fileExplorer");
        prop("label", label);
        prop("items", FileNode.json(items));
    }

    /** The paths chosen; several with Ctrl/⌘ or Shift. */
    public FileExplorer selected(java.util.List<String> paths) { return prop("selected", java.util.List.copyOf(paths)); }
    /** A file opened (double-click, Enter): its path. */
    public FileExplorer onOpen(java.util.function.Consumer<String> action) { return on("onOpen", v -> action.accept(Keys.text(v))); }
    public FileExplorer onSelectionChange(java.util.function.Consumer<java.util.List<String>> action) { return on("onSelectionChange", v -> action.accept(Keys.texts(v))); }
    /** Rename in its right-click menu (and F2): the path. */
    public FileExplorer onRename(java.util.function.Consumer<String> action) { return on("onRename", v -> action.accept(Keys.text(v))); }
    /** Delete in its right-click menu (and the Delete key): the path. */
    public FileExplorer onDelete(java.util.function.Consumer<String> action) { return on("onDelete", v -> action.accept(Keys.text(v))); }
}
