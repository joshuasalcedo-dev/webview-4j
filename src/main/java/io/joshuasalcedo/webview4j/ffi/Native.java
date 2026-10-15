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

    /** The native library's version, e.g. {@code 0.1.0}. */
    public static String version() {
        try {
            MemorySegment version = (MemorySegment) WV_VERSION.invokeExact();
            return version.reinterpret(Long.MAX_VALUE).getString(0);
        } catch (Throwable e) {
            throw new IllegalStateException("wv_version failed", e);
        }
    }
}
