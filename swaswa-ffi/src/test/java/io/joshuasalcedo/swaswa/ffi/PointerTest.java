package io.joshuasalcedo.swaswa.ffi;

import static io.joshuasalcedo.swaswa.ffi.ViewMethodsTest.until;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.foreign.MemorySegment;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * The pointer and the windows under it, and a person moving a window: what a drag between windows needs. The real
 * mouse moves only in the last test, run with -Dswaswa.mouse=true.
 */
@EnabledOnOs(OS.WINDOWS)
@Timeout(90)
class PointerTest {

    /** The next ENDED, past the MOVINGs (a real drag says where it goes ~30 times a second). */
    static WindowMove endOf(BlockingQueue<WindowMove> moves) throws InterruptedException {
        for (WindowMove m; (m = moves.poll(10, TimeUnit.SECONDS)) != null; ) {
            if (m.phase() == WindowMove.Phase.ENDED) return m;
        }
        return null;
    }

    static int[] center(MemorySegment hwnd) {
        int[] r = Win32.rect(hwnd);
        return new int[] {(r[0] + r[2]) / 2, (r[1] + r[3]) / 2};
    }

    @Test
    void theCursorIsWhereTheSystemSays() throws Exception {
        try (App app = App.start()) {
            int[] system = Win32.cursor();
            Cursor cursor = app.cursor().join();
            assertEquals(system[0], cursor.physicalX(), 2, "x: " + cursor);
            assertEquals(system[1], cursor.physicalY(), 2, "y: " + cursor);
            assertTrue(cursor.scale() > 0);
            assertEquals(cursor.physicalX() / cursor.scale(), cursor.x(), 0.01, "logical: physical / scale");
        }
    }

    @Test
    void theWindowAtAPointIsTheTopOneThere() throws Exception {
        try (App app = App.start()) {
            View a = app.open(WindowOptions.html("<p>a</p>").title("swaswa test: under a").size(420, 320).position(100, 100)).join();
            View b = app.open(WindowOptions.html("<p>b</p>").title("swaswa test: under b").size(420, 320).position(260, 180)).join();
            MemorySegment ha = Win32.window("swaswa test: under a"), hb = Win32.window("swaswa test: under b");
            int[] ra = Win32.rect(ha), rb = Win32.rect(hb);
            int[] alone = {ra[0] + 30, ra[1] + 60};
            int[] both = {(rb[0] + ra[2]) / 2, (rb[1] + ra[3]) / 2};

            assertEquals(Optional.of(a), app.windowAt(alone[0], alone[1]).join(), "a alone");
            ViewMethodsTest.front(b);
            assertEquals(Optional.of(b), until(() -> app.windowAt(both[0], both[1]).join(), Optional.of(b)::equals), "b on top where they overlap");
            ViewMethodsTest.front(a);
            assertEquals(Optional.of(a), until(() -> app.windowAt(both[0], both[1]).join(), Optional.of(a)::equals), "a brought forward: a");

            // looking past the top one (a window being dragged): the one below it there
            ViewMethodsTest.front(b);
            until(() -> app.windowAt(both[0], both[1]).join(), Optional.of(b)::equals);
            assertEquals(Optional.of(a), app.windowAt(both[0], both[1], b).join(), "below b: a");
            assertEquals(Optional.empty(), app.windowAt(alone[0] - 0, ra[1] + 5 - 200, b).join(), "below b where neither is: none");

            int[] beyond = {Math.max(ra[2], rb[2]) + 400, Math.max(ra[3], rb[3]) + 300};
            assertTrue(app.windowAt(beyond[0], beyond[1]).join().filter(v -> v.equals(a) || v.equals(b)).isEmpty(), "none of them there");
        }
    }

    @Test
    void aMoveSaysWhenItStartsAndEnds() throws Exception {
        try (App app = App.start()) {
            BlockingQueue<WindowMove> moves = new LinkedBlockingQueue<>();
            app.subscribe(WindowMove.class, moves::add);
            View view = app.open(WindowOptions.html("<p>m</p>").title("swaswa test: moved").size(400, 300).position(120, 120)).join();
            MemorySegment hwnd = Win32.window("swaswa test: moved");
            int[] before = Win32.rect(hwnd);

            Win32.moveAsAPersonWould(hwnd, before[0] + 150, before[1] + 90);
            WindowMove started = moves.poll(10, TimeUnit.SECONDS), ended = PointerTest.endOf(moves);
            assertEquals(view.id(), started.view());
            assertEquals(WindowMove.Phase.STARTED, started.phase());
            assertEquals(List.of(before[0], before[1], before[2] - before[0], before[3] - before[1]),
                    List.of(started.x(), started.y(), started.width(), started.height()), "where it was as the move began");
            int[] pointer = Win32.cursor();
            assertEquals(pointer[0], started.cursorX(), 5, "and where the pointer was (it may have moved a little since)");
            assertEquals(pointer[1], started.cursorY(), 5);
            assertNotNull(ended);
            assertEquals(WindowMove.Phase.ENDED, ended.phase());
            assertEquals(before[0] + 150, ended.x(), "where it was let go");
            assertEquals(before[1] + 90, ended.y());
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "swaswa.mouse", matches = "true")
    void aRealDragOfTheTitleBarSaysWhereItWasLetGo() throws Exception {
        try (App app = App.start()) {
            BlockingQueue<WindowMove> moves = new LinkedBlockingQueue<>();
            app.subscribe(WindowMove.class, moves::add);
            View dragged = app.open(WindowOptions.html("<p>d</p>").title("swaswa test: dragged").size(400, 300).position(120, 120)).join();
            app.open(WindowOptions.html("<p>t</p>").title("swaswa test: target").size(500, 400).position(700, 150)).join();
            MemorySegment hd = Win32.window("swaswa test: dragged"), ht = Win32.window("swaswa test: target");
            ViewMethodsTest.front(dragged);
            Thread.sleep(300);
            int[] rd = Win32.rect(hd);
            int[] over = center(ht);
            Win32.drag(rd[0] + 80, rd[1] + 12, over[0], over[1]);   // its title bar, onto the other window

            WindowMove started = moves.poll(10, TimeUnit.SECONDS), ended = PointerTest.endOf(moves);
            assertNotNull(started, "a real move began");
            assertEquals(WindowMove.Phase.STARTED, started.phase());
            assertEquals(WindowMove.Phase.ENDED, ended.phase());
            assertTrue(ended.x() > rd[0] + 300, "moved: " + ended);
            Cursor at = app.cursor().join();
            assertEquals(over[0], at.physicalX(), 3, "let go over the target");
        }
    }
}
