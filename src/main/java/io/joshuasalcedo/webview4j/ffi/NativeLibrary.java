package io.joshuasalcedo.webview4j.ffi;

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
 * <p>{@code -Dwebview4j.library=<path>} loads that file instead, e.g. a fresh {@code cargo build}.
 */
public final class NativeLibrary {

    static final String NAME = "webview4j";

    private NativeLibrary() {}

    private static final class Holder {
        static final SymbolLookup LOOKUP = load();
    }

    /** The library's exported functions; loads it on first use. */
    public static SymbolLookup lookup() {
        return Holder.LOOKUP;
    }

    /** This machine as the jar names it, e.g. {@code windows-x64}, {@code macos-aarch64}. */
    public static String platform() {
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
        String override = System.getProperty("webview4j.library");
        Path file = override != null ? Path.of(override) : extract();
        return SymbolLookup.libraryLookup(file, Arena.global());
    }

    private static Path extract() {
        String fileName = System.mapLibraryName(NAME);
        String resource = "/natives/" + platform() + "/" + fileName;
        byte[] bytes;
        try (InputStream in = NativeLibrary.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new UnsatisfiedLinkError("No native library for " + platform() + " on the classpath (" + resource + ")");
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
            throw new UncheckedIOException("Could not copy the native library to " + dir, e);
        }
        return file;
    }

    private static String hash(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
