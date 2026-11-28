package io.joshuasalcedo.swaswa.ffi;

import static io.joshuasalcedo.swaswa.ffi.ExceptionsTest.failure;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * What the operating system refuses, caused for real: a place only an administrator may write (needsElevation), a
 * file this user may not write (it would not help), and failures that are not about permission at all.
 */
@EnabledOnOs(OS.WINDOWS)
@Timeout(120)
class OsErrorsTest {

    /** A place only an administrator may write. */
    static final Path SYSTEM = Path.of(System.getenv("SystemRoot"), "System32", "swaswa-test");

    @Test
    void elevatedIsWhatWindowsSays() throws Exception {
        Process whoami = new ProcessBuilder("whoami", "/groups").redirectErrorStream(true).start();
        String groups = new String(whoami.getInputStream().readAllBytes());
        whoami.waitFor();
        boolean high = groups.contains("S-1-16-12288") || groups.contains("S-1-16-16384");   // high or system integrity
        assertEquals(high, App.elevated(), "App.elevated() agrees with the process's integrity level");
    }

    @Test
    void aSystemFolderNeedsAnAdministrator() throws Exception {
        assumeFalse(App.elevated(), "run as administrator: the system's folders can be written");

        PermissionException store = assertThrows(PermissionException.class, () -> NativeStore.open(SYSTEM.resolve("state.redb")));
        assertTrue(store.needsElevation(), "administrator rights would help: " + store);
        assertTrue(store.hint().contains("administrator") && store.hint().contains("NativeStore.directory"), store.hint());
        assertTrue(store.getMessage().contains("What to do:"), store.getMessage());

        try (App app = App.start()) {
            View view = app.open(WindowOptions.html("<p>x</p>").title("swaswa test: os").size(300, 200)).join();
            ViewMethodsTest.until(() -> view.eval("document.body?.textContent ?? null").join(), "x"::equals);

            PermissionException shot = failure(PermissionException.class, view.screenshot(SYSTEM.resolve("shot.png")));
            assertTrue(shot.needsElevation(), shot.toString());

            // written by the native side: the same, from the system's own answer (its error code with it)
            PermissionException recording = failure(PermissionException.class,
                    view.record(Path.of(System.getenv("SystemRoot"), "swaswa-test.gif")));
            assertTrue(recording.needsElevation(), recording.toString());
            assertEquals(5, recording.osError(), "Windows' ERROR_ACCESS_DENIED");
            assertEquals("view." + view.id() + ".record", recording.action());

            PermissionException profile = failure(PermissionException.class,
                    app.open(WindowOptions.html("<p>p</p>").headless(true).dataDirectory(SYSTEM.resolve("profile"))));
            assertTrue(profile.needsElevation(), profile.toString());
            assertTrue(profile.reason().contains("browser profile"), profile.reason());
        }
    }

    @Test
    void aFileThisUserMayNotWriteIsNotAboutAdministrators(@TempDir Path dir) throws Exception {
        Path locked = Files.writeString(dir.resolve("locked.redb"), "x");
        Files.setAttribute(locked, "dos:readonly", true);
        try {
            PermissionException store = assertThrows(PermissionException.class, () -> NativeStore.open(locked));
            assertFalse(store.needsElevation(), "a read-only file: administrator rights are not the answer: " + store);
            assertTrue(store.hint().contains("read-only"), store.hint());

            Path lockedGif = Files.writeString(dir.resolve("locked.gif"), "x");
            Files.setAttribute(lockedGif, "dos:readonly", true);
            try (App app = App.start()) {
                View view = app.open(WindowOptions.html("<p>x</p>").title("swaswa test: locked").size(300, 200)).join();
                ViewMethodsTest.until(() -> view.eval("document.body?.textContent ?? null").join(), "x"::equals);
                PermissionException recording = failure(PermissionException.class, view.record(lockedGif));
                assertFalse(recording.needsElevation(), recording.toString());
            } finally {
                Files.setAttribute(lockedGif, "dos:readonly", false);
            }
        } finally {
            Files.setAttribute(locked, "dos:readonly", false);
        }
    }

    @Test
    void aFileWhereAFolderShouldBeIsASystemFailure(@TempDir Path dir) throws Exception {
        Path file = Files.writeString(dir.resolve("a-file"), "x");
        try (App app = App.start()) {
            View view = app.open(WindowOptions.html("<p>x</p>").title("swaswa test: system").size(300, 200)).join();
            ViewMethodsTest.until(() -> view.eval("document.body?.textContent ?? null").join(), "x"::equals);

            SystemException shot = failure(SystemException.class, view.screenshot(file.resolve("inside").resolve("shot.png")));
            assertTrue(shot.hint().contains(file + " is a file where a folder should be"), "says which part: " + shot.hint());

            // the native side: a browser profile under a file
            SystemException profile = failure(SystemException.class,
                    app.open(WindowOptions.html("<p>p</p>").headless(true).dataDirectory(file.resolve("profile"))));
            assertNotNull(profile.osError(), "the system's own code: " + profile);
            assertTrue(profile.hint().contains(file + " is a file where a folder should be"), profile.hint());
        }
    }
}
