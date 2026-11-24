package io.joshuasalcedo.webview4j.ui.components.usecase.autoupdate;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import io.joshuasalcedo.webview4j.ui.UiException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Versions, changelogs, signed manifests, and the updater against a real server. */
class AutoUpdateTest extends ReleaseServer {

    @TempDir
    Path dir;

    @Test
    void versionsCompareAsReleasesNumberThem() {
        assertTrue(Version.of("1.10.0").newerThan(Version.of("1.9.9")));
        assertTrue(Version.of("2.0.0").newerThan(Version.of("2.0.0-rc.2")));
        assertTrue(Version.of("v1.2").compareTo(Version.of("1.2.0")) == 0);
        assertFalse(Version.is("unreleased"));
    }

    @Test
    void aChangelogIsItsVersions() {
        ReleaseNotes notes = ReleaseNotes.parse("""
                # Changelog

                ## 0.3.0 — 2026-12-01

                - Auto-update

                ## [0.2.0] - 2026-11-25
                - Layouts
                """);
        assertEquals(List.of("0.3.0", "0.2.0"), notes.releases().stream().map(ReleaseNotes.Release::version).toList());
        assertEquals("2026-11-25", notes.of("0.2.0").orElseThrow().date());
        assertEquals("- Auto-update", notes.of("0.3.0").orElseThrow().body());
        assertEquals(List.of("0.3.0"), notes.between(Version.of("0.2.0"), Version.of("0.3.0")).stream().map(ReleaseNotes.Release::version).toList());
        assertEquals(List.of("0.4.0", "0.3.0", "0.2.0", "0.1.0"), ReleaseNotes.bundled().releases().stream().map(ReleaseNotes.Release::version).toList(),
                "the app's own, from its jar");
    }

    @Test
    void anUpdateIsTakenOnlyWhenSignedNewerAndWhole() throws Exception {
        String[] keys = UpdateSignature.newKeys();
        byte[] installer = "an installer".getBytes();
        release("0.3.0", installer, keys[1]);

        Updater updater = Updater.of(url("/manifest.json"), keys[0], Version.of("0.2.0")).directory(dir);
        UpdateManifest found = updater.check().join().orElseThrow();
        assertEquals("0.3.0", found.version().text());

        List<Double> progress = new CopyOnWriteArrayList<>();
        Path file = updater.download(found, progress::add).join();
        assertArrayEquals(installer, Files.readAllBytes(file));
        assertEquals(1.0, progress.getLast());

        List<Path> ran = new CopyOnWriteArrayList<>();
        updater.installer(ran::add).install(file);
        assertEquals(List.of(file), ran);

        // the same version, or an older one: nothing to do (no going back)
        assertEquals(Optional.empty(), Updater.of(url("/manifest.json"), keys[0], Version.of("0.3.0")).check().join());

        // signed by someone else: refused
        String[] other = UpdateSignature.newKeys();
        release("0.3.0", installer, other[1]);
        CompletionException wrongKey = assertThrows(CompletionException.class, () -> updater.check().join());
        assertTrue(((UiException) wrongKey.getCause()).userMessage().contains("signature"));

        // the manifest changed after it was signed: refused
        release("0.3.0", installer, keys[1]);
        files.put("/manifest.json", new String(files.get("/manifest.json")).replace("0.3.0", "9.9.9").getBytes());
        assertThrows(CompletionException.class, () -> updater.check().join());

        // the installer is not the one the release says: thrown away
        release("0.3.0", installer, keys[1]);
        UpdateManifest again = updater.check().join().orElseThrow();
        files.put("/Notes-0.3.0.msi", "something else entirely".getBytes());
        CompletionException swapped = assertThrows(CompletionException.class, () -> updater.download(again, p -> {}).join());
        assertTrue(((UiException) swapped.getCause()).userMessage().contains("not what the release says"));
        assertFalse(Files.exists(dir.resolve("Notes-0.3.0.msi.part")));
    }

    @Test
    void onlyHttpsLeavesTheMachine() {
        Updater updater = Updater.of(URI.create("http://updates.example.com/manifest.json"), UpdateSignature.newKeys()[0], Version.of("0.1.0"));
        CompletionException e = assertThrows(CompletionException.class, () -> updater.check().join());
        assertTrue(((UiException) e.getCause()).userMessage().contains("https"));
    }

    @Test
    void theToolMakesTheKeyAndASignedManifest() throws Exception {
        Path key = dir.resolve("secret/update.key");
        var out = new java.io.ByteArrayOutputStream();
        var was = System.out;
        System.setOut(new java.io.PrintStream(out, true, StandardCharsets.UTF_8));
        try {
            UpdateTool.main(new String[] {"keygen", key.toString()});
        } finally {
            System.setOut(was);
        }
        String printed = out.toString(StandardCharsets.UTF_8);
        String publicKey = printed.lines().filter(l -> l.startsWith("update.publicKey=")).findFirst().orElseThrow().substring("update.publicKey=".length());
        assertFalse(printed.contains(Files.readString(key).trim()), "the private key is never printed");

        Path installer = dir.resolve("Notes-0.3.0.msi");
        Files.write(installer, "the 0.3.0 installer".getBytes());
        Path changelog = dir.resolve("CHANGELOG.md");
        Files.writeString(changelog, "# Changelog\n\n## 0.3.0 — 2026-12-01\n\n- Auto-update\n");
        Path manifest = dir.resolve("update/manifest.json");
        UpdateTool.main(new String[] {"manifest", "--app", "notes", "--version", "0.3.0", "--changelog", changelog.toString(),
                "--key-file", key.toString(), "--out", manifest.toString(),
                "--download", UpdateManifest.platform() + "=" + installer + "=" + url("/Notes-0.3.0.msi")});

        files.put("/manifest.json", Files.readAllBytes(manifest));
        files.put("/manifest.json.sig", Files.readAllBytes(Path.of(manifest + ".sig")));
        files.put("/Notes-0.3.0.msi", Files.readAllBytes(installer));
        UpdateManifest m = Updater.of(url("/manifest.json"), publicKey, Version.of("0.2.0")).check().join().orElseThrow();
        assertEquals("- Auto-update", m.notes(), "the version's changelog section");
        assertEquals("2026-12-01", m.date());
        assertEquals(Files.size(installer), m.forThisMachine().orElseThrow().size());
    }
}
