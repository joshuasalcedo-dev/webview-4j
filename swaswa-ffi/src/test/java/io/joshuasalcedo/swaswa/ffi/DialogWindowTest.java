package io.joshuasalcedo.swaswa.ffi;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * The system's dialogs, shown for real and answered by the test as a person would ({@link Win32#answer}): a path
 * typed, then OK, or Cancel. What the person chose is what Java gets.
 */
@EnabledOnOs(OS.WINDOWS)
@Timeout(120)
class DialogWindowTest {

    private static <T> T within(CompletableFuture<T> future) throws Exception {
        return future.get(30, TimeUnit.SECONDS);
    }

    @Test
    void fileDialogsGiveWhatWasChosenAndNothingWhenCancelled(@TempDir Path temp) throws Exception {
        Path dir = temp.toRealPath();   // the dialog names files by their long names, not C:\Users\JOHN~1
        Path a = Files.writeString(dir.resolve("a.txt"), "a");
        Path b = Files.writeString(dir.resolve("b.txt"), "b");
        try (App app = App.start()) {
            // one file
            CompletableFuture<Optional<Path>> one = app.openFile(new FileDialog().title("swaswa test: open").directory(dir));
            Win32.answer("swaswa test: open", a.toString(), Win32.IDOK);
            assertEquals(Optional.of(a), within(one), "the file typed in");

            CompletableFuture<Optional<Path>> none = app.openFile(new FileDialog().title("swaswa test: open, cancelled").directory(dir));
            Win32.answer("swaswa test: open, cancelled", null, Win32.IDCANCEL);
            assertEquals(Optional.empty(), within(none), "cancelled: nothing");

            // several files: the box takes them quoted, as the dialog writes them itself
            CompletableFuture<List<Path>> many = app.openFiles(new FileDialog().title("swaswa test: open many").directory(dir));
            Win32.answer("swaswa test: open many", "\"a.txt\" \"b.txt\"", Win32.IDOK);
            assertEquals(java.util.Set.of(a, b), java.util.Set.copyOf(within(many)), "both files");

            CompletableFuture<List<Path>> noneOfThem = app.openFiles(new FileDialog().title("swaswa test: open many, cancelled"));
            Win32.answer("swaswa test: open many, cancelled", null, Win32.IDCANCEL);
            assertEquals(List.of(), within(noneOfThem));

            // where to save: a new file's name
            Path target = dir.resolve("saved.txt");
            CompletableFuture<Optional<Path>> save = app.saveFile(new FileDialog().title("swaswa test: save").directory(dir)
                    .fileName("untitled.txt").filter("Text", "txt"));
            Win32.answer("swaswa test: save", target.toString(), Win32.IDOK);
            assertEquals(Optional.of(target), within(save), "where to save");
            assertFalse(Files.exists(target), "the dialog only says where: Java writes the file");

            CompletableFuture<Optional<Path>> notSaved = app.saveFile(new FileDialog().title("swaswa test: save, cancelled"));
            Win32.answer("swaswa test: save, cancelled", null, Win32.IDCANCEL);
            assertEquals(Optional.empty(), within(notSaved));

            // a folder
            Path folder = Files.createDirectories(dir.resolve("chosen"));
            CompletableFuture<Optional<Path>> picked = app.pickFolder(new FileDialog().title("swaswa test: folder").directory(dir));
            Win32.answer("swaswa test: folder", folder.toString(), Win32.IDOK);
            assertEquals(Optional.of(folder), within(picked), "the folder typed in");

            CompletableFuture<Optional<Path>> noFolder = app.pickFolder(new FileDialog().title("swaswa test: folder, cancelled"));
            Win32.answer("swaswa test: folder, cancelled", null, Win32.IDCANCEL);
            assertEquals(Optional.empty(), within(noFolder));
        }
    }

    @Test
    void messageBoxesAndConfirmAnswerWithTheButtonPressed() throws Exception {
        try (App app = App.start()) {
            View owner = app.open(WindowOptions.html("<p>owner</p>").title("swaswa test: owner").size(400, 300)).join();

            CompletableFuture<Boolean> yes = app.confirm("swaswa test: confirm", "Go on?");
            Win32.answer("swaswa test: confirm", null, Win32.IDOK);
            assertTrue(within(yes), "OK: true");

            CompletableFuture<Boolean> no = app.confirm("swaswa test: confirm, no", "Go on?");
            Win32.answer("swaswa test: confirm, no", null, Win32.IDCANCEL);
            assertFalse(within(no), "Cancel: false");

            CompletableFuture<MessageDialog.Answer> asked = app.message(MessageDialog.of("swaswa test: yes no", "Keep it?")
                    .buttons(MessageDialog.Buttons.YES_NO_CANCEL).level(MessageDialog.Level.ERROR).parent(owner));
            Win32.answer("swaswa test: yes no", null, 7);   // IDNO
            assertEquals(MessageDialog.Answer.NO, within(asked));

            CompletableFuture<MessageDialog.Answer> told = app.message(MessageDialog.of("swaswa test: info", "Done."));
            Win32.answer("swaswa test: info", null, Win32.IDOK);
            assertEquals(MessageDialog.Answer.OK, within(told));
        }
    }
}
