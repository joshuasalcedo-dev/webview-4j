package io.joshuasalcedo.webview4j;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionException;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** A window owned by another of the app's windows, and the windows' native handles. Windows only, like AppTest. */
@EnabledOnOs(OS.WINDOWS)
class OwnerTest {

    /** user32's GetWindow(hwnd, GW_OWNER): the window that owns this one, as Windows itself sees it. */
    static long ownerOf(long hwnd) throws Throwable {
        Linker linker = Linker.nativeLinker();
        MethodHandle getWindow = linker.downcallHandle(
                SymbolLookup.libraryLookup("user32", Arena.global()).findOrThrow("GetWindow"),
                FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        MemorySegment owner = (MemorySegment) getWindow.invokeExact(MemorySegment.ofAddress(hwnd), 4 /* GW_OWNER */);
        return owner.address();
    }

    @Test
    @Timeout(90)
    void anOwnedWindowIsOwnedByItsOwnersWindowAndClosesBeforeIt() throws Throwable {
        try (App app = App.start()) {
            BlockingQueue<Closed> closed = new LinkedBlockingQueue<>();
            app.subscribe(Closed.class, closed::add);
            View main = app.open(WindowOptions.html("<p>main</p>").title("Main").headless(true).size(800, 600)).join();
            View tool = app.open(WindowOptions.html("<p>tool</p>").title("Tool").owner(main).headless(true).size(300, 400)).join();
            View loose = app.open(WindowOptions.html("<p>loose</p>").headless(true).size(200, 200)).join();

            long mainHandle = main.nativeHandle().join();
            long toolHandle = tool.nativeHandle().join();
            assertNotEquals(0, mainHandle);
            assertNotEquals(mainHandle, toolHandle);
            assertEquals(mainHandle, ownerOf(toolHandle), "Windows sees the tool window as owned by the main one");
            assertEquals(0, ownerOf(mainHandle), "the main window has no owner");

            // an owned window can own one in turn; closing the first owner closes the chain, innermost first
            View palette = app.open(WindowOptions.blank().owner(tool).headless(true).size(100, 100)).join();
            assertEquals(toolHandle, ownerOf(palette.nativeHandle().join()));
            main.close().join();
            assertEquals(palette.id(), closed.poll(10, SECONDS).view());
            assertEquals(tool.id(), closed.poll(10, SECONDS).view());
            assertEquals(main.id(), closed.poll(10, SECONDS).view());
            assertEquals(1, app.views().join().size(), "a window it does not own stays open");
            assertEquals(loose.id(), app.views().join().get(0).id());
        }
    }

    @Test
    @Timeout(90)
    void whatCannotBeOwnedIsRefused() throws Exception {
        try (App app = App.start()) {
            View main = app.open(WindowOptions.html("<p>main</p>").headless(true).size(800, 600)).join();

            // an owner that is not open
            View gone = app.open(WindowOptions.blank().headless(true)).join();
            gone.close().join();
            CompletionException missing = assertThrows(CompletionException.class,
                    () -> app.open(WindowOptions.blank().owner(gone).headless(true)).join());
            assertTrue(missing.getCause().getMessage().contains("no view"), missing.getCause().getMessage());

            // an embedded view has no window of its own: no handle, and it cannot be owned
            View site = main.embed(WindowOptions.html("<p>site</p>"), 0, 0, 200, 100).join();
            CompletionException handle = assertThrows(CompletionException.class, () -> site.nativeHandle().join());
            assertTrue(handle.getCause().getMessage().contains("is embedded"), handle.getCause().getMessage());

            // but it can own a window: the one it sits in owns it
            View tool = app.open(WindowOptions.blank().owner(site).headless(true).size(100, 100)).join();
            try {
                assertEquals(main.nativeHandle().join(), ownerOf(tool.nativeHandle().join()));
            } catch (Throwable e) {
                throw new AssertionError(e);
            }

            // owner and ownerWindow are one or the other
            assertThrows(IllegalStateException.class, () -> WindowOptions.blank().owner(main).ownerWindow(1));
            assertThrows(IllegalStateException.class, () -> WindowOptions.blank().ownerWindow(1).owner(main));
        }
    }
}
