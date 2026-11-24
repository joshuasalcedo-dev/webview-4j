package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** Folders and files as a tree that opens folder by folder. */
public final class FileTree extends Component<FileTree> {

    public FileTree(String label, java.util.List<FileNode> items) {
        super("fileTree");
        prop("label", label);
        prop("items", FileNode.json(items));
    }

    /** The paths chosen; several with Ctrl/⌘ or Shift. */
    public FileTree selected(java.util.List<String> paths) { return prop("selected", java.util.List.copyOf(paths)); }
    /** A file opened (double-click, Enter): its path. */
    public FileTree onOpen(java.util.function.Consumer<String> action) { return on("onOpen", v -> action.accept(Keys.text(v))); }
    public FileTree onSelectionChange(java.util.function.Consumer<java.util.List<String>> action) { return on("onSelectionChange", v -> action.accept(Keys.texts(v))); }
    /** Rename in its right-click menu (and F2): the path. */
    public FileTree onRename(java.util.function.Consumer<String> action) { return on("onRename", v -> action.accept(Keys.text(v))); }
    /** Delete in its right-click menu (and the Delete key): the path. */
    public FileTree onDelete(java.util.function.Consumer<String> action) { return on("onDelete", v -> action.accept(Keys.text(v))); }
}
