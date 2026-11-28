package io.joshuasalcedo.swaswa.ffi;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Each way an action can fail, caused for real: the exception's class says which situation it is, its reason what
 * happened, its hint what to do, and its action which call it was.
 */
@EnabledOnOs(OS.WINDOWS)
@Timeout(120)
class ExceptionsTest {

    /** What {@code future} failed with, unwrapped from the CompletionException join() throws. */
    static <E extends WebViewException> E failure(Class<E> type, CompletableFuture<?> future) {
        CompletionException thrown = assertThrows(CompletionException.class, future::join);
        assertInstanceOf(type, thrown.getCause(), "a " + type.getSimpleName() + ": " + thrown.getCause());
        return type.cast(thrown.getCause());
    }

    static void saysWhatToDo(WebViewException e, String hintPart) {
        assertNotNull(e.hint(), "a hint: " + e);
        assertTrue(e.hint().contains(hintPart), "the hint says '" + hintPart + "': " + e.hint());
        assertTrue(e.getMessage().contains("What to do: " + e.hint()), "the message shows it, for a stack trace: " + e.getMessage());
        assertTrue(e.getMessage().startsWith(e.action() + ": " + e.reason()), "then what failed and why: " + e.getMessage());
    }

    @Test
    void aClosedViewSaysItWasClosed() throws Exception {
        try (App app = App.start()) {
            View view = app.open(WindowOptions.html("<p>x</p>").headless(true)).join();
            view.close().join();
            ViewClosedException closed = failure(ViewClosedException.class, view.setTitle("again"));
            assertEquals("view." + view.id() + ".title", closed.action(), "which call");
            assertTrue(closed.reason().contains("closed"), closed.reason());
            saysWhatToDo(closed, "Closed");
        }
    }

    @Test
    void whatAViewCannotDoSaysWhatToCallInstead() throws Exception {
        try (App app = App.start()) {
            View host = app.open(WindowOptions.html("<p>host</p>").headless(true)).join();
            View embedded = host.embed(WindowOptions.html("<p>in</p>"), 0, 0, 100, 100).join();
            NotSupportedException noWindow = failure(NotSupportedException.class, embedded.setTitle("x"));
            saysWhatToDo(noWindow, "place(");

            NotSupportedException noTools = failure(NotSupportedException.class, host.openDevTools());
            saysWhatToDo(noTools, "devtools(true)");
        }
    }

    @Test
    void aBadRequestABusyViewAndAPageWithoutTheCanvas(@TempDir Path dir) throws Exception {
        try (App app = App.start()) {
            View view = app.open(WindowOptions.html("<p>x</p>").title("swaswa test: errors").size(300, 200)).join();
            ViewMethodsTest.until(() -> view.eval("document.body?.textContent ?? null").join(), "x"::equals);

            InvalidRequestException bad = failure(InvalidRequestException.class, view.setCookie(Cookie.of("bad name", "x")));
            assertFalse(bad.reason().isBlank(), "why: " + bad.reason());

            Recording first = view.record(dir.resolve("a.gif")).join();
            ConflictException busy = failure(ConflictException.class, view.record(dir.resolve("b.gif")));
            saysWhatToDo(busy, "stop()");
            first.stop().join();

            PageException noCanvas = failure(PageException.class, view.drawFrame("screen", Frame.rgba(1, 1, new byte[4])));
            saysWhatToDo(noCanvas, "<canvas id=\"screen\">");
        }
    }

    @Test
    void anAppUsedWrongSaysHow() throws Exception {
        App app = App.start();
        AlreadyRunningException second = assertThrows(AlreadyRunningException.class, App::start, "one per process");
        assertTrue(second.hint().contains("one App per process"), second.hint());

        View view = app.open(WindowOptions.html("<p>x</p>").headless(true)).join();
        app.close();
        NotRunningException after = failure(NotRunningException.class, view.setTitle("after close"));
        assertNotNull(after.hint(), after.toString());
        assertTrue(after.hint().contains("start") || after.hint().contains("Start"), after.hint());

        // and everything is a WebViewException: one catch takes them all
        assertInstanceOf(WebViewException.class, after);
    }

    @Test
    void anExceptionWithoutAHintIsJustItsReason() {
        WebViewException plain = new WebViewException("something");
        assertEquals("something", plain.getMessage());
        assertNull(plain.hint());
        assertNull(plain.action());
        NativeLibraryException missing = new NativeLibraryException("no library", "add it");
        assertEquals("no library" + System.lineSeparator() + "  What to do: add it", missing.getMessage());
    }
}
