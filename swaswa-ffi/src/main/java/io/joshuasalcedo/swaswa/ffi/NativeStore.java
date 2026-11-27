package io.joshuasalcedo.swaswa.ffi;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * One file of keys and bytes in the native library's redb store (ACID: a write is
 * all there or not at all, even if the app is killed). By default in the system's place for an app's data:
 * {@code %LOCALAPPDATA%\<app>\state.redb}, {@code ~/Library/Application Support/<app>/state.redb},
 * {@code ~/.local/share/<app>/state.redb}.
 */
public final class NativeStore implements AutoCloseable {

    private static final Linker LINKER = Linker.nativeLinker();
    private static final ValueLayout.OfLong U64 = ValueLayout.JAVA_LONG;
    private static final MethodHandle OPEN = handle("wv_store_open", FunctionDescriptor.of(U64, ValueLayout.ADDRESS, U64));
    private static final MethodHandle PUT = handle("wv_store_put",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, U64, ValueLayout.ADDRESS, U64, ValueLayout.ADDRESS, U64));
    private static final MethodHandle GET = handle("wv_store_get",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, U64, ValueLayout.ADDRESS, U64, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle DELETE = handle("wv_store_delete",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, U64, ValueLayout.ADDRESS, U64));
    private static final MethodHandle KEYS = handle("wv_store_keys",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, U64, ValueLayout.ADDRESS, U64, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle FREE = handle("wv_store_free", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, U64));
    private static final MethodHandle CLOSE = handle("wv_store_close", FunctionDescriptor.ofVoid(U64));

    private final long handle;
    private final Path file;

    private NativeStore(long handle, Path file) {
        this.handle = handle;
        this.file = file;
    }

    /** The store in {@code file}, made if it is not there. */
    public static NativeStore open(Path file) {
        // the folder and the file first, in Java: what the system says about them, not a bare "could not be opened"
        try {
            Path folder = file.toAbsolutePath().getParent();
            if (folder != null) java.nio.file.Files.createDirectories(folder);
            if (java.nio.file.Files.exists(file) && !java.nio.file.Files.isWritable(file)) {
                throw new java.nio.file.AccessDeniedException(file.toString());
            }
            if (folder != null && !java.nio.file.Files.exists(file)) {
                java.nio.file.Files.deleteIfExists(java.nio.file.Files.createTempFile(folder, ".swaswa-check", ".tmp"));
            }
        } catch (java.io.IOException e) {
            throw OsErrors.of("the state store " + file + " could not be opened", e, file);
        }
        byte[] path = file.toAbsolutePath().toString().getBytes(StandardCharsets.UTF_8);
        try (Arena arena = Arena.ofConfined()) {
            long h = (long) OPEN.invokeExact(bytes(arena, path), (long) path.length);
            if (h == 0) throw new SystemException("the state store " + file + " could not be opened",
                    "It may be open in another program (one process at a time per store), or not a store: close the other, or choose another file.", null, null);
            return new NativeStore(h, file);
        } catch (WebViewException e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException("wv_store_open failed", e);
        }
    }

    /** The store of the app named {@code app}, in the system's place for an app's data. */
    public static NativeStore forApp(String app) {
        return open(directory(app).resolve("state.redb"));
    }

    /** The system's place for {@code app}'s data. */
    public static Path directory(String app) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String home = System.getProperty("user.home");
        String safe = app.replaceAll("[^A-Za-z0-9._ -]", "_");
        if (os.contains("win")) {
            String local = System.getenv("LOCALAPPDATA");
            return Path.of(local != null ? local : home + "\\AppData\\Local", safe);
        }
        if (os.contains("mac")) return Path.of(home, "Library", "Application Support", safe);
        String data = System.getenv("XDG_DATA_HOME");
        return Path.of(data != null && !data.isBlank() ? data : home + "/.local/share", safe);
    }

    public Path file() {
        return file;
    }

    public void put(String key, byte[] value) {
        byte[] k = key.getBytes(StandardCharsets.UTF_8);
        try (Arena arena = Arena.ofConfined()) {
            int r = (int) PUT.invokeExact(handle, bytes(arena, k), (long) k.length, bytes(arena, value), (long) value.length);
            if (r != 0) throw new IllegalStateException("the state store could not keep " + key);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException("wv_store_put failed", e);
        }
    }

    public Optional<byte[]> get(String key) {
        byte[] k = key.getBytes(StandardCharsets.UTF_8);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = arena.allocate(ValueLayout.ADDRESS);
            MemorySegment len = arena.allocate(U64);
            int r = (int) GET.invokeExact(handle, bytes(arena, k), (long) k.length, out, len);
            if (r == 0) return Optional.empty();
            if (r != 1) throw new IllegalStateException("the state store could not read " + key);
            return Optional.of(take(out, len));
        } catch (IllegalStateException e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException("wv_store_get failed", e);
        }
    }

    public boolean delete(String key) {
        byte[] k = key.getBytes(StandardCharsets.UTF_8);
        try (Arena arena = Arena.ofConfined()) {
            int r = (int) DELETE.invokeExact(handle, bytes(arena, k), (long) k.length);
            if (r < 0) throw new IllegalStateException("the state store could not delete " + key);
            return r == 1;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException("wv_store_delete failed", e);
        }
    }

    public List<String> keys(String prefix) {
        byte[] p = prefix.getBytes(StandardCharsets.UTF_8);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = arena.allocate(ValueLayout.ADDRESS);
            MemorySegment len = arena.allocate(U64);
            int r = (int) KEYS.invokeExact(handle, bytes(arena, p), (long) p.length, out, len);
            if (r != 0) throw new IllegalStateException("the state store could not list " + prefix);
            byte[] all = take(out, len);
            List<String> keys = new ArrayList<>();
            int start = 0;
            for (int i = 0; i <= all.length; i++) {
                if (i == all.length || all[i] == 0) {
                    if (i > start) keys.add(new String(all, start, i - start, StandardCharsets.UTF_8));
                    start = i + 1;
                }
            }
            return keys;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException("wv_store_keys failed", e);
        }
    }

    @Override
    public void close() {
        try {
            CLOSE.invokeExact(handle);
        } catch (Throwable e) {
            throw new IllegalStateException("wv_store_close failed", e);
        }
    }

    /** The bytes the library gave (and frees them). */
    private static byte[] take(MemorySegment out, MemorySegment len) throws Throwable {
        MemorySegment ptr = out.get(ValueLayout.ADDRESS, 0);
        long n = len.get(U64, 0);
        byte[] bytes = n == 0 ? new byte[0] : ptr.reinterpret(n).toArray(ValueLayout.JAVA_BYTE);
        FREE.invokeExact(ptr, n);
        return bytes;
    }

    private static MemorySegment bytes(Arena arena, byte[] bytes) {
        MemorySegment s = arena.allocate(Math.max(bytes.length, 1));
        MemorySegment.copy(bytes, 0, s, ValueLayout.JAVA_BYTE, 0, bytes.length);
        return s;
    }

    private static MethodHandle handle(String name, FunctionDescriptor descriptor) {
        return LINKER.downcallHandle(NativeLibrary.lookup().findOrThrow(name), descriptor);
    }
}
