package io.joshuasalcedo.swaswa.ffi;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.foreign.Arena;
import java.lang.foreign.SymbolLookup;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Finds and loads the native library, once. The jar carries it at
 * {@code natives/<os>-<arch>/webview4j.dll} (or {@code libwebview4j.so}, {@code libwebview4j.dylib});
 * it is copied to {@code <tmp>/webview-4j/<content hash>/}, so versions never overwrite each other
 * and a library already copied is reused.
 *
 * <p>{@code -Dswaswa.library=<path>} loads that file instead, e.g. a fresh {@code cargo build}.
 */
final class NativeLibrary {

    static final String NAME = "webview4j";

    private NativeLibrary() {}

    private static final class Holder {
        static final SymbolLookup LOOKUP = load();
    }

    /** The library's exported functions; loads it on first use. */
    static SymbolLookup lookup() {
        return Holder.LOOKUP;
    }

    /** This machine as the jar names it, e.g. {@code windows-x64}, {@code macos-aarch64}. */
    static String platform() {
        String name = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String os = name.startsWith("windows") ? "windows" : name.startsWith("mac") ? "macos" : "linux";
        String arch = switch (System.getProperty("os.arch")) {
            case "amd64", "x86_64" -> "x64";
            case "aarch64", "arm64" -> "aarch64";
            default -> System.getProperty("os.arch");
        };
        return os + "-" + arch;
    }

    private static SymbolLookup load() {
        String override = System.getProperty("swaswa.library");
        Path file = override != null ? Path.of(override) : extract();
        return SymbolLookup.libraryLookup(file, Arena.global());
    }

    private static Path extract() {
        String fileName = System.mapLibraryName(NAME);
        String resource = "/natives/" + platform() + "/" + fileName;
        byte[] bytes;
        try (InputStream in = NativeLibrary.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new NativeLibraryException("no native library for " + platform() + " on the class path (" + resource + ")",
                        "Add swaswa-ffi's natives for " + platform() + " (a build for this system and processor), or set "
                                + "-Dswaswa.library to the library file.");
            }
            bytes = in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Path dir = Path.of(System.getProperty("java.io.tmpdir"), "webview-4j", hash(bytes));
        Path file = dir.resolve(fileName);
        try {
            if (!Files.exists(file) || Files.size(file) != bytes.length) {
                Files.createDirectories(dir);
                // Written aside then moved, so another process never loads a half-written file.
                Path partial = Files.createTempFile(dir, NAME, ".partial");
                Files.write(partial, bytes);
                try {
                    Files.move(partial, file, StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException e) {
                    Files.deleteIfExists(partial);
                    if (!Files.exists(file)) throw e;   // another process won the race: use its copy
                }
            }
        } catch (IOException e) {
            WebViewException os = OsErrors.of("the native library could not be copied to " + dir, e, dir);
            throw new NativeLibraryException(os.reason(), os.hint() + " Or set -Djava.io.tmpdir to a writable folder, or "
                    + "-Dswaswa.library to the library file.", e);
        }
        Thread.ofVirtual().name("swaswa-native-cleanup").start(() -> removeOthers(dir));
        return file;
    }

    /**
     * Other versions' copies, left by earlier builds or releases: each is tens of megabytes, and a developer's every
     * build makes one. Those untouched for a while go; one in use (loaded by a running app) cannot be deleted on
     * Windows and stays, as does any just written by another process starting now.
     */
    static void removeOthers(Path kept) {
        Path root = kept.getParent();
        long before = System.currentTimeMillis() - java.util.concurrent.TimeUnit.MINUTES.toMillis(10);
        try (java.util.stream.Stream<Path> versions = Files.list(root)) {
            for (Path version : versions.toList()) {
                if (version.equals(kept) || !Files.isDirectory(version)) continue;
                try {
                    if (Files.getLastModifiedTime(version).toMillis() > before) continue;
                    try (java.util.stream.Stream<Path> files = Files.list(version)) {
                        for (Path f : files.toList()) Files.deleteIfExists(f);
                    }
                    Files.deleteIfExists(version);
                } catch (IOException inUse) {
                    // loaded by a running app, or being written: left for a later start
                }
            }
        } catch (IOException ignored) {
            // nothing to tidy
        }
    }

    private static String hash(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
