package io.joshuasalcedo.webview4j.ui;

import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.FileDialog;
import io.joshuasalcedo.webview4j.MessageDialog;
import io.joshuasalcedo.webview4j.View;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** A desktop window's host: the system's dialogs, the disk, the window's notice. */
final class DesktopHost implements Host {

    private final App app;
    private final View view;
    private final UiWindow window;

    DesktopHost(App app, View view, UiWindow window) {
        this.app = app;
        this.view = view;
        this.window = window;
    }

    @Override
    public boolean onWeb() {
        return false;
    }

    @Override
    public boolean supports(Feature feature) {
        return feature != Feature.TRAY;
    }

    private FileDialog dialog(String title, String... extensions) {
        FileDialog d = new FileDialog().parent(view);
        if (title != null) d.title(title);
        if (extensions.length > 0) d.filter(String.join(", ", extensions), extensions);
        return d;
    }

    @Override
    public CompletableFuture<Optional<PickedFile>> openFile(String title, String... extensions) {
        return app.openFile(dialog(title, extensions)).thenApply(p -> p.map(DiskFile::new));
    }

    @Override
    public CompletableFuture<List<PickedFile>> openFiles(String title, String... extensions) {
        return app.openFiles(dialog(title, extensions)).thenApply(paths -> paths.stream().<PickedFile>map(DiskFile::new).toList());
    }

    @Override
    public CompletableFuture<Boolean> saveFile(String fileName, byte[] content) {
        String ext = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.') + 1) : null;
        FileDialog d = new FileDialog().parent(view).fileName(fileName);
        if (ext != null && !ext.isBlank()) d.filter(ext.toUpperCase(java.util.Locale.ROOT), ext);
        return app.saveFile(d).thenApply(target -> {
            if (target.isEmpty()) return false;
            try {
                Files.write(target.get(), content);
                return true;
            } catch (IOException e) {
                throw new UiException("The file could not be saved", "It could not be written to " + target.get().getFileName()
                        + ". Check that you can write there, and try again.", UiException.Level.WARNING, e);
            }
        });
    }

    @Override
    public CompletableFuture<Optional<Path>> pickFolder(String title) {
        return app.pickFolder(dialog(title));
    }

    @Override
    public CompletableFuture<Void> message(String title, String text, UiException.Level level) {
        MessageDialog.Level l = switch (level) {
            case INFO -> MessageDialog.Level.INFO;
            case WARNING -> MessageDialog.Level.WARNING;
            case ERROR -> MessageDialog.Level.ERROR;
        };
        return app.message(MessageDialog.of(title, text).level(l).parent(view)).thenApply(a -> null);
    }

    @Override
    public CompletableFuture<Boolean> confirm(String title, String text) {
        return app.message(MessageDialog.of(title, text).buttons(MessageDialog.Buttons.OK_CANCEL).parent(view))
                .thenApply(a -> a == MessageDialog.Answer.OK || a == MessageDialog.Answer.YES);
    }

    @Override
    public CompletableFuture<Boolean> notify(String title, String text) {
        return window.notify(title, text, UiException.Level.INFO).thenApply(v -> false);
    }

    /** A file on the disk: read when its bytes are asked for. */
    private record DiskFile(Path file) implements PickedFile {
        @Override public String name() { return file.getFileName().toString(); }

        @Override
        public long size() {
            try {
                return Files.size(file);
            } catch (IOException e) {
                return -1;
            }
        }

        @Override
        public byte[] bytes() {
            try {
                return Files.readAllBytes(file);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override public Optional<Path> path() { return Optional.of(file); }
    }
}
