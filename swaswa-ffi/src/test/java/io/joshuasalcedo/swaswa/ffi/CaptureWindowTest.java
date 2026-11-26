package io.joshuasalcedo.swaswa.ffi;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** Pictures and videos of the app's own windows, in a real (visible) window. */
@EnabledOnOs(OS.WINDOWS)
class CaptureWindowTest {

    @TempDir
    Path dir;

    static final String PAGE = """
            <body style="margin:0;background:#0f6cbd;color:white;font:32px sans-serif">
            <p id="t">frame 0</p>
            <script>let n = 0; setInterval(() => document.getElementById('t').textContent = 'frame ' + (++n), 50)</script>
            </body>""";

    /** A PNG's width and height, from its header. */
    static int[] size(byte[] png) {
        ByteBuffer b = ByteBuffer.wrap(png);
        return new int[] {b.getInt(16), b.getInt(20)};
    }

    @Test
    @Timeout(90)
    void aPictureAndRecordingsOfAWindow() throws Exception {
        try (App app = App.start()) {
            View view = app.open(WindowOptions.html(PAGE).size(480, 320).position(40, 40).title("capture me")).join();
            Thread.sleep(800);   // drawn

            byte[] png = view.screenshot().get(30, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals("PNG", new String(png, 1, 3), "a PNG");
            int[] wh = size(png);
            Geometry g = view.geometry().join();
            assertTrue(wh[0] >= g.width() * 0.9 && wh[0] <= g.width() * g.scaleFactor() * 1.2 + 40, "the window's width: " + wh[0] + " " + g);
            int[] half = size(view.screenshot(0.5).join());
            assertEquals(Math.round(wh[0] * 0.5), half[0], 1, "smaller by half");
            Path file = view.screenshot(dir.resolve("shots/window.png")).join();
            assertTrue(Files.size(file) > 1000);

            // a GIF: made here, several frames
            Recording gif = view.record(dir.resolve("tour.gif"), Recording.Options.gif().fps(10).scale(0.5)).get(30, java.util.concurrent.TimeUnit.SECONDS);
            Thread.sleep(1500);
            Recording.Result done = gif.stop().get(30, java.util.concurrent.TimeUnit.SECONDS);
            assertTrue(done.frames() >= 8, "about 10 a second: " + done);
            byte[] head = Files.readAllBytes(done.file());
            assertEquals("GIF89a", new String(head, 0, 6));
            assertEquals(Math.round(wh[0] * 0.5), done.width(), 1);

            // one recording at a time per window
            Recording again = view.record(dir.resolve("again.gif")).join();
            CompletionException twice = assertThrows(CompletionException.class, () -> view.record(dir.resolve("twice.gif")).join());
            assertTrue(twice.getCause().getMessage().contains("already being recorded"), twice.getCause().getMessage());
            again.stop().get(30, java.util.concurrent.TimeUnit.SECONDS);

            // MP4: Windows' own encoder (Media Foundation), no ffmpeg needed
            Recording mp4 = view.record(dir.resolve("tour.mp4"), Recording.Options.mp4().fps(20)).join();
            Thread.sleep(1200);
            Recording.Result video = mp4.stop().get(30, java.util.concurrent.TimeUnit.SECONDS);
            byte[] box = Files.readAllBytes(video.file());
            assertEquals("ftyp", new String(box, 4, 4), "an MP4");
            assertTrue(video.frames() >= 15 && Files.size(video.file()) > 2000, video + " " + Files.size(video.file()));

            // a hidden (headless) window is captured too: tests and CI take pictures without showing anything
            View hidden = app.open(WindowOptions.html(PAGE).headless(true).size(400, 300)).join();
            Thread.sleep(800);
            byte[] unseen = hidden.screenshot().get(30, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals("PNG", new String(unseen, 1, 3));
            assertTrue(size(unseen)[0] > 100, "its size: " + size(unseen)[0]);
        }
    }
}
