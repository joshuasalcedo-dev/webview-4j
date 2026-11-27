package io.joshuasalcedo.swaswa.ffi;

import static io.joshuasalcedo.swaswa.ffi.ViewMethodsTest.until;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * Pictures Java draws on a page's canvas ({@link View#drawFrame}): exactly the pixels sent, PNG and JPEG at their own
 * size, only the latest when Java sends faster than the page draws, and fast enough to animate.
 */
@EnabledOnOs(OS.WINDOWS)
@Timeout(120)
class CanvasTest {

    static final String PAGE = "<body style='margin:0'><canvas id=screen></canvas><canvas id=photo></canvas><canvas id=source width=40 height=30></canvas>";

    static View page(App app) throws InterruptedException {
        View view = app.open(WindowOptions.html(PAGE).headless(true)).join();
        until(() -> view.eval("document.getElementById('screen') !== null").join(), Boolean.TRUE::equals);
        return view;
    }

    /** The canvas's pixels as the page has them: its size, then each byte. */
    @SuppressWarnings("unchecked")
    static List<Number> pixels(View view, String canvas) {
        return (List<Number>) view.eval("""
                (() => { const c = document.getElementById('%s');
                  return [c.width, c.height, ...c.getContext('2d').getImageData(0, 0, c.width, c.height).data]; })()""".formatted(canvas)).join();
    }

    @Test
    void theExactPixelsSentAreOnTheCanvas() throws Exception {
        try (App app = App.start()) {
            View view = page(app);
            byte[] rgba = new byte[4 * 3 * 4];
            for (int i = 0; i < rgba.length; i++) rgba[i] = (byte) (i * 5);
            for (int i = 3; i < rgba.length; i += 4) rgba[i] = (byte) 255;   // opaque: a canvas keeps those exactly
            assertTrue(view.drawFrame("screen", Frame.rgba(4, 3, rgba)).get(15, TimeUnit.SECONDS), "drawn");

            List<Number> drawn = pixels(view, "screen");
            assertEquals(4, drawn.get(0).intValue(), "the canvas sized to the picture");
            assertEquals(3, drawn.get(1).intValue());
            for (int i = 0; i < rgba.length; i++) assertEquals(rgba[i] & 0xFF, drawn.get(i + 2).intValue(), "byte " + i);

            // a new size: the canvas follows
            byte[] wide = new byte[8 * 2 * 4];
            java.util.Arrays.fill(wide, (byte) 255);
            assertTrue(view.drawFrame("screen", Frame.rgba(8, 2, wide)).join());
            assertEquals(List.of(8, 2), pixels(view, "screen").subList(0, 2).stream().map(Number::intValue).toList());
        }
    }

    @Test
    void pngAndJpegAreDrawnAtTheirOwnSize() throws Exception {
        try (App app = App.start()) {
            View view = page(app);
            assertTrue(view.drawFrame("photo", Frame.png(AppAndOptionsTest.png(16))).join());
            List<Number> png = pixels(view, "photo");
            assertEquals(List.of(16, 16), png.subList(0, 2).stream().map(Number::intValue).toList(), "a PNG's own size");
            assertEquals(List.of(0x31, 0x57, 0xD5, 0xFF), png.subList(2, 6).stream().map(Number::intValue).toList(), "its colour");

            // a JPEG, made by the page from a red canvas (Java has no encoder here)
            String url = (String) view.eval("""
                    (() => { const c = document.getElementById('source'), g = c.getContext('2d');
                      g.fillStyle = 'rgb(200, 30, 30)'; g.fillRect(0, 0, 40, 30); return c.toDataURL('image/jpeg', 0.95); })()""").join();
            byte[] jpeg = Base64.getDecoder().decode(url.substring(url.indexOf(',') + 1));
            assertTrue(view.drawFrame("photo", Frame.jpeg(jpeg)).join());
            List<Number> photo = pixels(view, "photo");
            assertEquals(List.of(40, 30), photo.subList(0, 2).stream().map(Number::intValue).toList(), "a JPEG's own size");
            assertEquals(200, photo.get(2).intValue(), 8, "red, give or take the JPEG");
            assertEquals(30, photo.get(3).intValue(), 8);
        }
    }

    @Test
    void wrongPicturesAndMissingCanvasesAreRefused() throws Exception {
        try (App app = App.start()) {
            View view = page(app);
            assertThrows(IllegalArgumentException.class, () -> Frame.rgba(2, 2, new byte[15]), "not 2 × 2 × 4 bytes");
            assertThrows(IllegalArgumentException.class, () -> Frame.rgba(0, 2, new byte[0]));
            assertThrows(IllegalArgumentException.class, () -> Frame.png(new byte[] {1, 2, 3, 4, 5, 6, 7, 8}), "not a PNG");
            assertThrows(IllegalArgumentException.class, () -> Frame.jpeg(new byte[] {1, 2, 3}), "not a JPEG");
            assertThrows(IllegalArgumentException.class, () -> view.drawFrame("not a name", Frame.rgba(1, 1, new byte[4])));

            CompletionException missing = assertThrows(CompletionException.class,
                    () -> view.drawFrame("nowhere", Frame.rgba(1, 1, new byte[4])).join());
            assertTrue(missing.getCause().getMessage().contains("no <canvas id=\"nowhere\">"), missing.getCause().getMessage());

            // a broken PNG: the page cannot decode it, and says so
            byte[] broken = java.util.Arrays.copyOf(AppAndOptionsTest.png(8), 20);
            CompletionException undecodable = assertThrows(CompletionException.class, () -> view.drawFrame("photo", Frame.png(broken)).join());
            assertNotNull(undecodable.getCause().getMessage());
        }
    }

    @Test
    void sentFasterThanDrawnOnlyTheLatestIsDrawn() throws Exception {
        try (App app = App.start()) {
            View view = page(app);
            List<CompletableFuture<Boolean>> sent = new ArrayList<>();
            for (int n = 0; n < 40; n++) {
                byte[] rgba = new byte[64 * 64 * 4];
                for (int i = 0; i < rgba.length; i += 4) { rgba[i] = (byte) n; rgba[i + 3] = (byte) 255; }
                sent.add(view.drawFrame("screen", Frame.rgba(64, 64, rgba)));
            }
            CompletableFuture.allOf(sent.toArray(CompletableFuture[]::new)).get(30, TimeUnit.SECONDS);
            assertTrue(sent.getLast().join(), "the last is always drawn");
            long skipped = sent.stream().filter(f -> !f.join()).count();
            assertTrue(skipped > 0, "some skipped: the page draws only the latest");
            assertEquals(39, pixels(view, "screen").get(2).intValue(), "the last picture is what is shown");
        }
    }

    @Test
    void thePagesPenIsDrawnByJavaAndComesBack() throws Exception {
        try (App app = App.start()) {
            int width = 200, height = 100;
            byte[] rgba = new byte[width * height * 4];
            java.util.Arrays.fill(rgba, (byte) 255);
            // Java: the points the page sends, drawn as black squares; the picture sent back before the call answers
            record Point(double x, double y) {}
            record Stroke(java.util.List<Point> points) {}
            app.handle("pen", call -> {
                View from = call.view();
                for (Point p : call.as(Stroke.class).points()) {
                    int x = (int) p.x(), y = (int) p.y();
                    for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                        int i = ((y + dy) * width + (x + dx)) * 4;
                        rgba[i] = rgba[i + 1] = rgba[i + 2] = 0;
                    }
                }
                from.drawFrame("screen", Frame.rgba(width, height, rgba.clone())).join();
                return "drawn";
            });
            View view = page(app);
            // the page: a stroke of points, sent as a pen does, then what its canvas shows
            view.eval("""
                    window.__pen = null;
                    (async () => {
                      // 20 strokes, as a pen sends them: the median is how far behind the pen the line is
                      const times = [];
                      for (let i = 0; i < 20; i++) {
                        const t = performance.now();
                        await __webview4j.invoke('pen', { points: [{ x: 20, y: 20 }, { x: 60, y: 50 }, { x: 150, y: 80 }] });
                        times.push(performance.now() - t);
                      }
                      times.sort((a, b) => a - b);
                      const ms = times[10], first = times[19], g = document.getElementById('screen').getContext('2d');
                      const at = (x, y) => g.getImageData(x, y, 1, 1).data[0];
                      window.__pen = { ms, slowest: first, inked: [at(20, 20), at(60, 50), at(150, 80)], blank: at(100, 10) };
                    })(); null""").join();
            Object took = until(() -> view.eval("window.__pen").join(), java.util.Objects::nonNull);
            Map<?, ?> result = (Map<?, ?>) took;
            assertEquals(List.of(0L, 0L, 0L), result.get("inked"), "each point drawn by Java, on the page's canvas: " + result);
            assertEquals(255L, result.get("blank"), "and nothing else");
            double ms = ((Number) result.get("ms")).doubleValue();
            System.out.println("pen: a stroke to Java and its picture back: median " + Math.round(ms) + " ms, slowest " + result.get("slowest") + " ms");
            assertTrue(ms < 34, "a stroke comes back within two frames: " + ms + " ms");
        }
    }

    @Test
    void fastEnoughToAnimate() throws Exception {
        try (App app = App.start()) {
            View view = page(app);
            int width = 640, height = 480;
            byte[] rgba = new byte[width * height * 4];
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            int frames = 0;
            while (System.nanoTime() < until) {
                byte shade = (byte) (frames * 7);
                for (int i = 0; i < rgba.length; i += 4) { rgba[i] = shade; rgba[i + 1] = (byte) (i >> 10); rgba[i + 3] = (byte) 255; }
                assertTrue(view.drawFrame("screen", Frame.rgba(width, height, rgba)).get(10, TimeUnit.SECONDS));
                frames++;
            }
            double fps = frames / 2.0;
            System.out.println("canvas: " + width + "×" + height + " RGBA, each waited for: " + fps + " frames a second");
            assertTrue(fps >= 20, "at least 20 frames a second at 640×480, each drawn before the next: " + fps);
        }
    }
}
