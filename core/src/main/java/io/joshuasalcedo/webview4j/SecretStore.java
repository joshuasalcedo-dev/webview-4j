package io.joshuasalcedo.webview4j;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

/**
 * Secrets in the system's own vault, encrypted for the signed-in user: Windows' Credential Manager, the macOS
 * Keychain, the Secret Service on Linux. For what must never sit in a file: a refresh token, an API key. Each secret
 * is named by a service (the app) and an account.
 * <pre>{@code
 * SecretStore vault = SecretStore.of("Office");
 * vault.put("ana@example.com", refreshToken);
 * Optional<String> token = vault.get("ana@example.com");
 * }</pre>
 */
public final class SecretStore {

    private static final Linker LINKER = Linker.nativeLinker();
    private static final ValueLayout.OfLong U64 = ValueLayout.JAVA_LONG;
    private static final MethodHandle SET = handle("wv_secret_set",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, U64, ValueLayout.ADDRESS, U64, ValueLayout.ADDRESS, U64));
    private static final MethodHandle GET = handle("wv_secret_get",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, U64, ValueLayout.ADDRESS, U64, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle DELETE = handle("wv_secret_delete",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, U64, ValueLayout.ADDRESS, U64));
    private static final MethodHandle FREE = handle("wv_store_free", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, U64));

    private final String service;

    private SecretStore(String service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    /** The secrets of {@code service} (the app's name). */
    public static SecretStore of(String service) {
        return new SecretStore(service);
    }

    public void put(String account, String secret) {
        byte[] s = service.getBytes(StandardCharsets.UTF_8);
        byte[] a = account.getBytes(StandardCharsets.UTF_8);
        byte[] v = secret.getBytes(StandardCharsets.UTF_8);
        try (Arena arena = Arena.ofConfined()) {
            int r = (int) SET.invokeExact(bytes(arena, s), (long) s.length, bytes(arena, a), (long) a.length, bytes(arena, v), (long) v.length);
            if (r != 0) throw new IllegalStateException("the system's vault did not keep the secret");
        } catch (IllegalStateException e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException("wv_secret_set failed", e);
        }
    }

    public Optional<String> get(String account) {
        byte[] s = service.getBytes(StandardCharsets.UTF_8);
        byte[] a = account.getBytes(StandardCharsets.UTF_8);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = arena.allocate(ValueLayout.ADDRESS);
            MemorySegment len = arena.allocate(U64);
            int r = (int) GET.invokeExact(bytes(arena, s), (long) s.length, bytes(arena, a), (long) a.length, out, len);
            if (r == 0) return Optional.empty();
            if (r != 1) throw new IllegalStateException("the system's vault could not be read");
            MemorySegment ptr = out.get(ValueLayout.ADDRESS, 0);
            long n = len.get(U64, 0);
            String secret = new String(n == 0 ? new byte[0] : ptr.reinterpret(n).toArray(ValueLayout.JAVA_BYTE), StandardCharsets.UTF_8);
            FREE.invokeExact(ptr, n);
            return Optional.of(secret);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException("wv_secret_get failed", e);
        }
    }

    /** Whether there was one. */
    public boolean delete(String account) {
        byte[] s = service.getBytes(StandardCharsets.UTF_8);
        byte[] a = account.getBytes(StandardCharsets.UTF_8);
        try (Arena arena = Arena.ofConfined()) {
            int r = (int) DELETE.invokeExact(bytes(arena, s), (long) s.length, bytes(arena, a), (long) a.length);
            if (r < 0) throw new IllegalStateException("the system's vault could not delete the secret");
            return r == 1;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException("wv_secret_delete failed", e);
        }
    }

    private static MemorySegment bytes(Arena arena, byte[] bytes) {
        MemorySegment seg = arena.allocate(Math.max(bytes.length, 1));
        MemorySegment.copy(bytes, 0, seg, ValueLayout.JAVA_BYTE, 0, bytes.length);
        return seg;
    }

    private static MethodHandle handle(String name, FunctionDescriptor descriptor) {
        return LINKER.downcallHandle(NativeLibrary.lookup().findOrThrow(name), descriptor);
    }
}
