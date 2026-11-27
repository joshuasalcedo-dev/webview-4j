package io.joshuasalcedo.swaswa.ffi;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Dev or prod, and where an app's data goes: a run from a build's class folders keeps it in that build folder. */
class AppModeTest {

    @Test
    void aRunFromTheBuildsClassesIsDevAndItsDataStaysInTheBuildFolder() {
        assertEquals(AppMode.DEV, AppMode.current(), "the tests run from target/test-classes");
        Path target = AppMode.buildDirectory().orElseThrow();
        assertEquals("target", target.getFileName().toString());
        assertTrue(Files.exists(target.getParent().resolve("pom.xml")), "swaswa-ffi's own target: " + target);
        assertEquals(target.resolve("swaswa-dev").resolve("MyApp"), NativeStore.directory("MyApp"), "not %LOCALAPPDATA%");
    }

    @Test
    void aBuildFolderIsOneBesideABuildFile(@TempDir Path root) throws Exception {
        Path project = Files.createDirectories(root.resolve("app"));
        Files.writeString(project.resolve("pom.xml"), "<project/>");
        Path classes = Files.createDirectories(project.resolve("target/classes"));
        assertEquals(Optional.of(project.resolve("target")), AppMode.buildOf(classes));

        Path gradle = Files.createDirectories(root.resolve("g"));
        Files.writeString(gradle.resolve("build.gradle.kts"), "");
        assertEquals(Optional.of(gradle.resolve("build")), AppMode.buildOf(Files.createDirectories(gradle.resolve("build/classes/java/main"))));

        Path installed = Files.createDirectories(root.resolve("Program Files/MyApp/target/lib"));   // no build file beside it
        assertEquals(Optional.empty(), AppMode.buildOf(installed), "an installed app's folder named target is not a build's");
    }
}
