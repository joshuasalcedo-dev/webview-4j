package io.joshuasalcedo.webview4j.ui;

import io.joshuasalcedo.webview4j.ui.state.Context;
import io.joshuasalcedo.webview4j.ui.state.ContextProperty;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Where a screen is shown, and what it can ask of it: files to open and save, a message, a question, a notification.
 * The same calls in a desktop window (the system's dialogs, the disk) and a browser tab of a {@link WebHost} (the
 * browser's file chooser and downloads, dialogs in the page, the browser's notifications); {@link #supports} says what
 * is there before a screen offers it. In {@code render()} and handlers, {@link #current()} is the screen's own.
 * <pre>{@code
 * button("Import").onClick(() -> Host.current().openFile("Import contacts", "csv")
 *         .thenAccept(file -> file.ifPresent(f -> contacts.importCsv(f.text()))));
 * button("Export").onClick(() -> Host.current().saveFile("contacts.csv", contacts.csv()));
 * }</pre>
 */
public interface Host {

    /** What a host may have. */
    enum Feature {
        /** A native window to move, resize, close and embed sites in ({@link UiWindow#view()}). */
        NATIVE_WINDOW,
        /** Choosing files to open and where to save one. */
        FILE_DIALOGS,
        /** Choosing a folder, and its path. */
        FOLDERS,
        /** Paths to the person's files (on the web, a file is its name and bytes). */
        FILE_SYSTEM,
        /** Messages and questions. */
        MESSAGE_BOXES,
        /** Notifications outside the page (the system's, the browser's). */
        NOTIFICATIONS,
        /** An icon in the system tray or the menu bar (not yet in webview-4j). */
        TRAY
    }

    /** The host in the context: set for every screen's render and handlers. */
    ContextProperty<Host> CURRENT = ContextProperty.of("webview4j.host");

    /** The current screen's host. */
    static Host current() {
        return Context.get(CURRENT);
    }

    /** A browser tab of a {@link WebHost}, not a desktop window. */
    boolean onWeb();

    boolean supports(Feature feature);

    /** One file to open, of these extensions ({@code "csv"}, {@code "png"}; none: any); empty if cancelled. */
    CompletableFuture<Optional<PickedFile>> openFile(String title, String... extensions);

    /** Files to open; empty if cancelled. */
    CompletableFuture<List<PickedFile>> openFiles(String title, String... extensions);

    /** Saves {@code content}: the desktop asks where (true when written), the web downloads it (true when started). */
    CompletableFuture<Boolean> saveFile(String fileName, byte[] content);

    /**
     * A folder; empty if cancelled. Only the desktop has one to give ({@link Feature#FOLDERS}): on the web the future
     * fails with a {@link UiException} saying so.
     */
    CompletableFuture<Optional<Path>> pickFolder(String title);

    /** A message to read, with OK. */
    CompletableFuture<Void> message(String title, String text, UiException.Level level);

    /** A question: true for OK, false for Cancel. */
    CompletableFuture<Boolean> confirm(String title, String text);

    /**
     * Tells the person something, even when they are looking elsewhere: the browser's notification (it asks the
     * first time) or a note in the page; on the desktop, the window's notice. True when shown outside the page.
     */
    CompletableFuture<Boolean> notify(String title, String text);

    /**
     * Opens a link outside the app: the system's browser (or mail app) on the desktop, a new tab on the web. Only
     * http, https and mailto: anything else fails, so text from elsewhere cannot start a program.
     */
    CompletableFuture<Void> openUrl(String url);

    /** Whether {@code url} may be opened: http, https or mailto. */
    static boolean openable(String url) {
        if (url == null) return false;
        String u = url.trim().toLowerCase(java.util.Locale.ROOT);
        return (u.startsWith("https://") || u.startsWith("http://") || u.startsWith("mailto:")) && !u.contains("\n") && !u.contains("\"") && !u.contains(" ");
    }

    /** A file chosen to open: its name, its size, its bytes; on the desktop, its path too. */
    interface PickedFile {
        String name();

        long size();

        byte[] bytes();

        /** The bytes as UTF-8 text. */
        default String text() {
            return new String(bytes(), java.nio.charset.StandardCharsets.UTF_8);
        }

        /** Where it is (the desktop); empty on the web, where a page never sees a path. */
        Optional<Path> path();
    }
}
