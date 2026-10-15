package io.joshuasalcedo.webview4j.ffi;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/**
 * The functions {@code crates/webview/src/lib.rs} exports, one downcall handle each.
 * Handles are static constants, which keeps them visible to GraalVM native-image.
 */
public final class Native {

    private Native() {}

    private static final Linker LINKER = Linker.nativeLinker();

    private static final MethodHandle WV_VERSION = LINKER.downcallHandle(
            NativeLibrary.lookup().findOrThrow("wv_version"),
            FunctionDescriptor.of(ValueLayout.ADDRESS));

    private static final MethodHandle WV_ACTIONS = LINKER.downcallHandle(
            NativeLibrary.lookup().findOrThrow("wv_actions"),
            FunctionDescriptor.of(ValueLayout.ADDRESS));

    /** The native library's version, e.g. {@code 0.1.0}. */
    public static String version() {
        try {
            return string((MemorySegment) WV_VERSION.invokeExact());
        } catch (Throwable e) {
            throw new IllegalStateException("wv_version failed", e);
        }
    }

    /** Every action the library knows, as {@code <group> <name> <code>} lines. */
    public static String actions() {
        try {
            return string((MemorySegment) WV_ACTIONS.invokeExact());
        } catch (Throwable e) {
            throw new IllegalStateException("wv_actions failed", e);
        }
    }

    /** A NUL-terminated string the library owns; copied, never freed here. */
    private static String string(MemorySegment pointer) {
        return pointer.reinterpret(Long.MAX_VALUE).getString(0);
    }
}
