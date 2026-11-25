package io.joshuasalcedo.webview4j.fx;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

import javafx.stage.Stage;

/**
 * A JavaFX stage's native window handle. JavaFX does not give it out; this finds it by a title no other window has
 * (set for a moment, on the FX thread, and put back), with user32's {@code FindWindowW}: no internal JavaFX classes,
 * no {@code --add-opens}.
 */
final class NativeWindows {

    private NativeWindows() {}

    static boolean supported() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }

    /** The stage's HWND, or 0 when it is not found. Call on the FX thread, while the stage is showing. */
    static long handleOf(Stage stage) {
        if (!supported()) throw new UnsupportedOperationException("webview-4j-fx docks webviews on Windows only, for now");
        String title = stage.getTitle();
        String probe = "webview4j-fx-" + UUID.randomUUID();
        stage.setTitle(probe);
        try {
            return findWindow(probe);
        } finally {
            stage.setTitle(title);
        }
    }

    private static long findWindow(String title) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment name = arena.allocateFrom(title, StandardCharsets.UTF_16LE);
            MemorySegment hwnd = (MemorySegment) User32.FIND_WINDOW.invokeExact(MemorySegment.NULL, name);
            return hwnd.address();
        } catch (Throwable e) {
            throw new IllegalStateException("FindWindowW failed", e);
        }
    }

    private static final class User32 {
        static final MethodHandle FIND_WINDOW = Linker.nativeLinker().downcallHandle(
                SymbolLookup.libraryLookup("user32", Arena.global()).find("FindWindowW").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    }
}
