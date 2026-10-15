package io.joshuasalcedo.webview4j;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.Consumer;

/**
 * The functions {@code crates/webview/src/lib.rs} exports, one downcall handle each.
 * Handles are static constants, which keeps them visible to GraalVM native-image.
 *
 * <p>{@link #run} blocks a thread with the event loop; {@link #call} is made from any other thread
 * and blocks until the loop answers. Replies and events are JSON.
 */
final class Native {

    private Native() {}

    private static final Linker LINKER = Linker.nativeLinker();

    private static final MethodHandle WV_VERSION = downcall("wv_version", FunctionDescriptor.of(ValueLayout.ADDRESS));
    private static final MethodHandle WV_ACTIONS = downcall("wv_actions", FunctionDescriptor.of(ValueLayout.ADDRESS));
    private static final MethodHandle WV_RUN = downcall("wv_run",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    private static final MethodHandle WV_CALL = downcall("wv_call",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle WV_SUBMIT = downcall("wv_submit", FunctionDescriptor.of(ValueLayout.ADDRESS,
            ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
    private static final MethodHandle WV_QUIT = downcall("wv_quit", FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN));
    private static final MethodHandle WV_FREE = downcall("wv_free", FunctionDescriptor.ofVoid(ValueLayout.ADDRESS));

    /** The C function pointer the loop calls with each event: {@link #onEvent}. */
    private static final MemorySegment ON_EVENT = upcall();

    /** Where {@link #onEvent} hands events while {@link #run} runs. */
    private static volatile Consumer<String> listener;

    /** The native library's version, e.g. {@code 0.1.0}. */
    static String version() {
        try {
            return string((MemorySegment) WV_VERSION.invokeExact());
        } catch (Throwable e) {
            throw new IllegalStateException("wv_version failed", e);
        }
    }

    /** Every action the library knows, as {@code <group> <name> <code>} lines. */
    static String actions() {
        try {
            return string((MemorySegment) WV_ACTIONS.invokeExact());
        } catch (Throwable e) {
            throw new IllegalStateException("wv_actions failed", e);
        }
    }

    /**
     * Runs the event loop on this thread until {@link #quit}; on macOS this must be the main thread.
     * {@code onEvent} gets every event as JSON ({@code {"type":"ready"}} first) on this thread: it must
     * return quickly and must not call {@link #call}, which would wait on itself. Returns the loop's
     * exit code.
     *
     * @throws IllegalStateException if an event loop is already running
     */
    static int run(Consumer<String> onEvent) {
        listener = onEvent;
        try {
            int code = (int) WV_RUN.invokeExact(ON_EVENT);
            if (code == -1) throw new IllegalStateException("the event loop is already running, or it failed");
            return code;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException("wv_run failed", e);
        } finally {
            listener = null;
        }
    }

    /**
     * Hands an action to the event loop and returns at once, from any thread. Returns null when
     * taken: the reply arrives as the event {@code {"type":"reply","request":<request>,...}}.
     * Otherwise returns the JSON error. {@code view} and {@code payload} may be null.
     */
    static String submit(Action action, String view, String payload, long request) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment refused = (MemorySegment) WV_SUBMIT.invokeExact(
                    action.code(), cString(arena, view), cString(arena, payload), request);
            if (refused.equals(MemorySegment.NULL)) return null;
            try {
                return string(refused);
            } finally {
                WV_FREE.invokeExact(refused);
            }
        } catch (Throwable e) {
            throw new IllegalStateException("wv_submit " + action.group() + " " + action.wireName() + " failed", e);
        }
    }

    /**
     * Performs an action and returns its JSON reply: {@code {"ok":true,...}} or
     * {@code {"ok":false,"error":"..."}}. {@code view} and {@code payload} may be null.
     */
    static String call(Action action, String view, String payload) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment reply = (MemorySegment) WV_CALL.invokeExact(
                    action.code(), cString(arena, view), cString(arena, payload));
            try {
                return string(reply);
            } finally {
                WV_FREE.invokeExact(reply);
            }
        } catch (Throwable e) {
            throw new IllegalStateException("wv_call " + action.group() + " " + action.wireName() + " failed", e);
        }
    }

    /** Closes every window and makes {@link #run} return. False if no loop is running. */
    static boolean quit() {
        try {
            return (boolean) WV_QUIT.invokeExact();
        } catch (Throwable e) {
            throw new IllegalStateException("wv_quit failed", e);
        }
    }

    /** Called by the native loop. An exception escaping an upcall kills the JVM, so none may. */
    private static void onEvent(MemorySegment json) {
        try {
            Consumer<String> consumer = listener;
            if (consumer != null) consumer.accept(string(json));
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private static MethodHandle downcall(String name, FunctionDescriptor descriptor) {
        return LINKER.downcallHandle(NativeLibrary.lookup().findOrThrow(name), descriptor);
    }

    private static MemorySegment upcall() {
        try {
            MethodHandle target = MethodHandles.lookup().findStatic(Native.class, "onEvent",
                    MethodType.methodType(void.class, MemorySegment.class));
            return LINKER.upcallStub(target, FunctionDescriptor.ofVoid(ValueLayout.ADDRESS), Arena.global());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static MemorySegment cString(Arena arena, String value) {
        return value == null ? MemorySegment.NULL : arena.allocateFrom(value);
    }

    /** A NUL-terminated UTF-8 string from the library, copied into Java. */
    private static String string(MemorySegment pointer) {
        return pointer.reinterpret(Long.MAX_VALUE).getString(0);
    }
}
