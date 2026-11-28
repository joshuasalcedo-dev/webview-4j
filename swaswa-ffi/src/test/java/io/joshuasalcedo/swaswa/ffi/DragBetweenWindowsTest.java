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
 * A page's own drag and drop, from one of the app's windows to another: the data a page puts on the drag is what the
 * other page gets (one engine for both). The real mouse: -Dswaswa.mouse=true.
 */
@EnabledOnOs(OS.WINDOWS)
@EnabledIfSystemProperty(named = "swaswa.mouse", matches = "true")
@Timeout(90)
class DragBetweenWindowsTest {

    static final String FROM = """
            <body style="margin:0;font:16px system-ui">
            <div id=card draggable=true style="position:absolute;left:40px;top:40px;width:200px;height:120px;background:#3157d5;color:white">a card</div>
            <script>
              document.getElementById('card').addEventListener('dragstart', (e) => {
                e.dataTransfer.setData('application/x-swaswa-card', JSON.stringify({ id: 42, title: 'a card' }));
                e.dataTransfer.setData('text/plain', 'a card');
                e.dataTransfer.effectAllowed = 'move';
              });
              document.getElementById('card').addEventListener('dragend', (e) => { window.__ended = e.dataTransfer.dropEffect; });
            </script>""";

    static final String TO = """
            <body style="margin:0">
            <div id=zone style="position:absolute;inset:0;background:#eef">drop here</div>
            <script>
              const zone = document.getElementById('zone');
              zone.addEventListener('dragover', (e) => { e.preventDefault(); e.dataTransfer.dropEffect = 'move'; });
              zone.addEventListener('drop', (e) => {
                e.preventDefault();
                window.__got = { card: e.dataTransfer.getData('application/x-swaswa-card'), text: e.dataTransfer.getData('text/plain') };
              });
            </script>""";

    @Test
    void whatOnePagePutsOnTheDragTheOtherGets() throws Exception {
        try (App app = App.start()) {
            View from = app.open(WindowOptions.html(FROM).title("swaswa test: cards").size(400, 300).position(100, 120)).join();
            View to = app.open(WindowOptions.html(TO).title("swaswa test: board").size(400, 300).position(700, 120)).join();
            until(() -> from.eval("document.getElementById('card') !== null").join(), Boolean.TRUE::equals);
            until(() -> to.eval("document.getElementById('zone') !== null").join(), Boolean.TRUE::equals);
            MemorySegment hf = Win32.window("swaswa test: cards"), ht = Win32.window("swaswa test: board");
            ViewMethodsTest.front(from);
            Thread.sleep(400);

            // the card's middle, in screen pixels: the window's client area plus the card's place in the page
            int[] frame = Win32.rect(hf);
            double scale = from.geometry().join().scaleFactor();
            int titleBar = Win32.frameHeight(hf);
            int cardX = frame[0] + (int) Math.round((40 + 100) * scale) + 8, cardY = frame[1] + titleBar + (int) Math.round((40 + 60) * scale);
            int[] board = PointerTest.center(ht);
            Win32.drag(cardX, cardY, board[0], board[1]);

            Object got = until(() -> to.eval("window.__got ?? null").join(), java.util.Objects::nonNull);
            assertNotNull(got, "the other window's page got a drop");
            java.util.Map<?, ?> data = (java.util.Map<?, ?>) got;
            assertEquals("{\"id\":42,\"title\":\"a card\"}", data.get("card"), "the page's own data, as it put it");
            assertEquals("a card", data.get("text"));
            assertEquals("move", until(() -> from.eval("window.__ended ?? null").join(), java.util.Objects::nonNull), "and the first page knows it was moved");
        }
    }
}
