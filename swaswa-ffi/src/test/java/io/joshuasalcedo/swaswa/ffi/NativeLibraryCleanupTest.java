package io.joshuasalcedo.swaswa.ffi;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The native library's copies of other versions go once they are old; the one in use, and new ones, stay. */
class NativeLibraryCleanupTest {

    static Path version(Path root, String name, Instant modified) throws Exception {
        Path dir = Files.createDirectories(root.resolve(name));
        Files.write(dir.resolve("webview4j.dll"), new byte[1024]);
        Files.setLastModifiedTime(dir, FileTime.from(modified));
        return dir;
    }

    @Test
    void oldCopiesOfOtherVersionsGo(@TempDir Path root) throws Exception {
        Instant now = Instant.now();
        Path kept = version(root, "kept", now.minus(2, ChronoUnit.DAYS));
        Path old = version(root, "old", now.minus(1, ChronoUnit.HOURS));
        Path fresh = version(root, "fresh", now.minus(1, ChronoUnit.MINUTES));
        NativeLibrary.removeOthers(kept);
        assertTrue(Files.exists(kept.resolve("webview4j.dll")), "the one this app uses");
        assertFalse(Files.exists(old), "another version's, untouched for an hour: gone");
        assertTrue(Files.exists(fresh), "one just written (another app starting): kept");
    }
}
