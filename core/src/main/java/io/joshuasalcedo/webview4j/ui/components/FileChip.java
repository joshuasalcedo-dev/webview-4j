package io.joshuasalcedo.webview4j.ui.components;

import io.joshuasalcedo.webview4j.ui.Component;

/** A file as a card or inline: its icon, name and size, an upload's progress, open and remove. */
public final class FileChip extends Component<FileChip> {

    public FileChip(String name) {
        super("fileChip");
        prop("name", name);
    }

    public FileChip size(long bytes) { return prop("size", bytes); }
    public FileChip inline() { return prop("variant", "inline"); }
    /** An upload's progress, 0 to 100. */
    public FileChip progress(double progress) { return prop("progress", progress); }
    public FileChip status(String status) { return prop("status", status); }
    public FileChip error(boolean error) { return prop("error", error); }
    public FileChip onOpen(Runnable action) { return on("onOpen", v -> action.run()); }
    public FileChip onRemove(Runnable action) { return on("onRemove", v -> action.run()); }
}
