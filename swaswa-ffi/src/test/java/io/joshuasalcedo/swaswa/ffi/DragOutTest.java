package io.joshuasalcedo.swaswa.ffi;

import static io.joshuasalcedo.swaswa.ffi.ExceptionsTest.failure;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.foreign.MemorySegment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Files dragged out of a window: refused when there is nothing to drag, and (with the real mouse, -Dswaswa.mouse=true)
 * dropped on another window, which gets exactly those files.
 */
@EnabledOnOs(OS.WINDOWS)
@Timeout(90)
class DragOutTest {

    @Test
    void nothingToDragIsRefused(@TempDir Path dir) throws Exception {
        try (App app = App.start()) {
            View view = app.open(WindowOptions.html("<p>d</p>").headless(true)).join();
            assertThrows(IllegalArgumentException.class, () -> view.dragFiles(List.of()));
            SystemException missing = failure(SystemException.class, view.dragFiles(List.of(dir.resolve("not-there.txt"))));
            assertTrue(missing.reason().contains("not-there.txt") && missing.hint().contains("exist"), missing.toString());
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "swaswa.mouse", matches = "true")
    void draggedOntoAnotherWindowItGetsThoseFiles(@TempDir Path dir) throws Exception {
        Path file = Files.writeString(dir.resolve("report.txt"), "dragged out");
        try (App app = App.start()) {
            BlockingQueue<FileDrop> drops = new LinkedBlockingQueue<>();
            app.subscribe(FileDrop.class, drops::add);
            View from = app.open(WindowOptions.html("<p>from</p>").title("swaswa test: drag from").size(400, 300).position(100, 120)).join();
            View to = app.open(WindowOptions.html("<p>to</p>").title("swaswa test: drag to").size(400, 300).position(700, 120).fileDrop(true)).join();
            MemorySegment hf = Win32.window("swaswa test: drag from"), ht = Win32.window("swaswa test: drag to");
            int[] a = PointerTest.center(hf), b = PointerTest.center(ht);
            ViewMethodsTest.front(from);
            Thread.sleep(400);

            Win32.mouseTo(a[0], a[1]);
            Thread.sleep(150);
            Win32.mouseButton(true);
            Thread.sleep(150);
            CompletableFuture<View.DropEffect> effect = from.dragFiles(List.of(file));   // the button held
            Thread.sleep(300);
            for (int i = 1; i <= 25; i++) {
                Win32.mouseTo(a[0] + (b[0] - a[0]) * i / 25, a[1] + (b[1] - a[1]) * i / 25);
                Thread.sleep(30);
            }
            Thread.sleep(300);
            Win32.mouseButton(false);

            View.DropEffect done = effect.get(15, TimeUnit.SECONDS);
            FileDrop dropped = null;
            for (FileDrop d; (d = drops.poll(5, TimeUnit.SECONDS)) != null; ) {
                if (d.kind() == FileDrop.Kind.DROP) {
                    dropped = d;
                    break;
                }
            }
            assertNotNull(dropped, "the other window got a drop (the effect: " + done + ")");
            assertEquals(to.id(), dropped.view());
            assertEquals(List.of(file.toRealPath()), dropped.paths().stream().map(p -> {
                try {
                    return p.toRealPath();
                } catch (java.io.IOException e) {
                    return p;
                }
            }).toList(), "exactly that file");
        }
    }
}
