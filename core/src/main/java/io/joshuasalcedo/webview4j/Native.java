package io.joshuasalcedo.webview4j;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * The native library's three functions (PROTOCOL.md §6), one downcall handle each. Handles are
 * static constants, which keeps them visible to GraalVM native-image. Frames go in through
 * {@link #send} and come out on the consumer {@link #run} was given.
 */
final class Native {

    private Native() {}

    /** {@link #send}: the frame was taken. */
    static final int SENT = 0;
    /** {@link #send}: no event loop is running. */
    static final int NOT_RUNNING = 1;
    /** {@link #send}: the frame is malformed or over a limit. */
    static final int MALFORMED = 2;

    private static final Linker LINKER = Linker.nativeLinker();

    private static final MethodHandle WV_VERSION = downcall("wv_version", FunctionDescriptor.of(ValueLayout.ADDRESS));
    private static final MethodHandle WV_RUN = downcall("wv_run",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    private static final MethodHandle WV_SEND = downcall("wv_send",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

    /** The C function pointer the loop calls with each frame: {@link #onMessage}. */
    private static final MemorySegment ON_MESSAGE = upcall();

    /** Where {@link #onMessage} hands frames while {@link #run} runs. */
    private static volatile Consumer<byte[]> listener;

    /** True while a thread is inside {@link #run}. */
    private static final AtomicBoolean RUNNING = new AtomicBoolean();

    /** The native library's version, e.g. {@code 0.1.0}. */
    static String version() {
        try {
            return ((MemorySegment) WV_VERSION.invokeExact()).reinterpret(Long.MAX_VALUE).getString(0);
        } catch (Throwable e) {
            throw new IllegalStateException("wv_version failed", e);
        }
    }

    /**
     * Runs the event loop on this thread until {@code app.quit}; on macOS this must be the main
     * thread. {@code onMessage} gets every frame, mostly on this thread: it must return quickly.
     * Returns the loop's exit code.
     *
     * @throws IllegalStateException if an event loop is already running
     */
    static int run(Consumer<byte[]> onMessage) {
        // Refused before the listener is touched: a second run must not take the first one's frames.
        if (!RUNNING.compareAndSet(false, true)) throw new IllegalStateException("the event loop is already running");
        listener = onMessage;
        try {
            int code = (int) WV_RUN.invokeExact(ON_MESSAGE);
            if (code == -1) throw new IllegalStateException("the event loop is already running, or it failed");
            return code;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Throwable e) {
            throw new IllegalStateException("wv_run failed", e);
        } finally {
            listener = null;
            RUNNING.set(false);
        }
    }

    /** Hands one frame to the loop, from any thread: {@link #SENT}, {@link #NOT_RUNNING} or {@link #MALFORMED}. */
    static int send(byte[] frame) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment bytes = arena.allocate(Math.max(frame.length, 1));
            MemorySegment.copy(frame, 0, bytes, ValueLayout.JAVA_BYTE, 0, frame.length);
            return (int) WV_SEND.invokeExact(bytes, (long) frame.length);
        } catch (Throwable e) {
            throw new IllegalStateException("wv_send failed", e);
        }
    }

    /** Called by the native loop. An exception escaping an upcall kills the JVM, so none may. */
    private static void onMessage(MemorySegment frame, long length) {
        try {
            Consumer<byte[]> consumer = listener;
            if (consumer != null) consumer.accept(frame.reinterpret(length).toArray(ValueLayout.JAVA_BYTE));
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private static MethodHandle downcall(String name, FunctionDescriptor descriptor) {
        return LINKER.downcallHandle(NativeLibrary.lookup().findOrThrow(name), descriptor);
    }

    private static MemorySegment upcall() {
        try {
            MethodHandle target = MethodHandles.lookup().findStatic(Native.class, "onMessage",
                    MethodType.methodType(void.class, MemorySegment.class, long.class));
            return LINKER.upcallStub(target, FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG), Arena.global());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
