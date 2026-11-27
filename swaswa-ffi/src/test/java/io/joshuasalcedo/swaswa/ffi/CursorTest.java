package io.joshuasalcedo.swaswa.ffi;

import static io.joshuasalcedo.swaswa.ffi.ViewMethodsTest.until;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.foreign.MemorySegment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * The cursor Windows shows over a page (read from Windows, not the page's CSS): the page's own, not the window's
 * arrow over it. The real pointer moves (no clicks): -Dswaswa.mouse=true.
 */
@EnabledOnOs(OS.WINDOWS)
@EnabledIfSystemProperty(named = "swaswa.mouse", matches = "true")
@Timeout(60)
class CursorTest {

    @Test
    void overThePageTheCursorIsThePagesOwn() throws Exception {
        try (App app = App.start()) {
            View view = app.open(WindowOptions.html("""
                    <body style="margin:0">
                    <div data-webview4j-drag-region style="height:150px;background:#3157d5"></div>
                    <input style="display:block;width:100%;height:150px;font-size:30px;box-sizing:border-box">
                    <div style="height:150px">plain</div>""").title("swaswa test: cursors").size(500, 480).position(200, 150)).join();
            until(() -> view.eval("document.readyState").join(), "complete"::equals);
            ViewMethodsTest.front(view);
            MemorySegment hwnd = Win32.window("swaswa test: cursors");
            int[] r = Win32.rect(hwnd);
            int titleBar = Win32.frameHeight(hwnd);
            double scale = view.geometry().join().scaleFactor();
            int x = (r[0] + r[2]) / 2, top = r[1] + titleBar;
            long arrow = Win32.systemCursor(32512), ibeam = Win32.systemCursor(32513);

            Win32.mouseTo(x, top + (int) (75 * scale * 1.1));            // over the drag region: a hand
            Win32.mouseTo(x + 2, top + (int) (75 * scale * 1.1));
            long overRegion = until(Win32::shownCursor, c -> c != arrow);
            assertNotEquals(arrow, overRegion, "over a drag region: not the arrow (the page's grab hand)");

            Win32.mouseTo(x, top + (int) (225 * scale * 1.1));           // over the text box: the I-beam
            Win32.mouseTo(x + 2, top + (int) (225 * scale * 1.1));
            assertEquals(ibeam, (long) until(Win32::shownCursor, c -> c == ibeam), "over a text box: the I-beam");

            Win32.mouseTo(x, top + (int) (375 * scale * 1.1));           // over plain text's line: the arrow
            Win32.mouseTo(x + 2, top + (int) (375 * scale * 1.1));
            assertEquals(arrow, (long) until(Win32::shownCursor, c -> c == arrow), "elsewhere: the arrow");
        }
    }
}
