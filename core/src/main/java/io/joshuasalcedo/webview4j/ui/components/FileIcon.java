package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** A file's icon by its name (the kind of file it is), or a folder's. */
public final class FileIcon extends Component<FileIcon> {

    public FileIcon(String name) {
        super("fileIcon");
        prop("name", name);
    }

    public FileIcon folder() { return prop("folder", true); }
    public FileIcon size(double size) { return prop("size", size); }
}
