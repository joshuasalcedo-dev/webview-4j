package io.joshuasalcedo.swaswa.ffi;

import static io.joshuasalcedo.swaswa.ffi.ViewMethodsTest.until;
import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.lang.foreign.MemorySegment;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** The app's own methods, and the window options that only show on a real window. */
@EnabledOnOs(OS.WINDOWS)
@Timeout(120)
class AppAndOptionsTest {

    @Test
    void quitStopsTheLoopOnce() throws Exception {
        App app = App.start();
        app.open(WindowOptions.html("<p>q</p>").headless(true)).join();
        assertTrue(app.quit(), "the loop was running");
        assertNotNull(app.whenStopped().get(15, TimeUnit.SECONDS), "it stopped, with its exit code");
        assertFalse(app.quit(), "stopped: nothing to quit");
        app.close();
    }

    @Test
    void theNextWindowWithATitleOpensWhereItWasPlaced() throws Exception {
        try (App app = App.start()) {
            app.placeNext("swaswa test: placed", new Geometry(130, 140, 420, 300, 1, false, false, false, false, true));
            View placed = app.open(WindowOptions.html("<p>p</p>").title("swaswa test: placed").size(800, 600)).join();
            Geometry g = placed.geometry().join();
            assertEquals(130, g.x(), 2, "placeNext: its place");
            assertEquals(140, g.y(), 2);
            assertEquals(420, g.width(), 2, "and size");
            placed.close().join();

            // a place no window took, forgotten: the next opens where its options say
            app.placeNext("swaswa test: forgotten", new Geometry(130, 140, 420, 300, 1, false, false, false, false, true));
            app.clearPlacements();
            View free = app.open(WindowOptions.html("<p>f</p>").title("swaswa test: forgotten").size(500, 350).position(220, 230)).join();
            Geometry h = free.geometry().join();
            assertEquals(220, h.x(), 2, "clearPlacements: its own place");
            assertEquals(500, h.width(), 2);
        }
    }

    @Test
    void aRememberedWindowOpensWhereItWasLeft() throws Exception {
        String name = "swaswa-test-" + UUID.randomUUID();
        try (App app = App.start()) {
            View first = app.open(WindowOptions.html("<p>r</p>").title("swaswa test: remembered").size(460, 330).position(100, 100).remember(name)).join();
            first.setPosition(180, 190).join();
            until(() -> first.geometry().join(), g -> Math.abs(g.x() - 180) < 2);
            Thread.sleep(600);   // its place written as it changes
            first.close().join();

            View again = app.open(WindowOptions.html("<p>r</p>").title("swaswa test: remembered").size(800, 600).position(10, 10).remember(name)).join();
            Geometry g = again.geometry().join();
            assertEquals(180, g.x(), 2, "remember: where it was left, not where its options say");
            assertEquals(190, g.y(), 2);
            assertEquals(460, g.width(), 2, "and its size");
        } finally {
            WindowPlaces.forget(name);
        }
    }

    @Test
    void aDataDirectoryKeepsAPagesDataAndOnlyItsOwn() throws Exception {
        // not a @TempDir: the browser's own process lets go of its files a moment after the app has closed
        Path dir = Files.createDirectories(Path.of("target", "test-data", UUID.randomUUID().toString())).toAbsolutePath();
        Path site = Files.createDirectories(dir.resolve("site"));
        Files.writeString(site.resolve("index.html"), "<!doctype html><title>kept</title>");
        Path profile = Files.createDirectories(dir.resolve("profile")), other = Files.createDirectories(dir.resolve("other"));
        try (App app = App.start(); URLClassLoader loader = new URLClassLoader(new URL[] {dir.toUri().toURL()})) {
            app.serveShared("site", loader);
            WindowOptions page = WindowOptions.url(App.SHARED_URL + "index.html").headless(true);

            View first = app.open(page.dataDirectory(profile)).join();
            until(() -> first.eval("document.title").join(), "kept"::equals);
            first.eval("localStorage.setItem('kept', 'yes')").join();
            assertEquals(profile, app.views().join().stream().filter(v -> v.id().equals(first.id())).findFirst().orElseThrow().dataDirectory(),
                    "the view says where its data is");
            first.close().join();

            View again = app.open(WindowOptions.url(App.SHARED_URL + "index.html").headless(true).dataDirectory(profile)).join();
            assertEquals("yes", until(() -> again.eval("document.title === 'kept' ? localStorage.getItem('kept') : null").join(), "yes"::equals),
                    "the same directory: the page's data kept");

            View elsewhere = app.open(WindowOptions.url(App.SHARED_URL + "index.html").headless(true).dataDirectory(other)).join();
            until(() -> elsewhere.eval("document.title").join(), "kept"::equals);
            assertNull(elsewhere.eval("localStorage.getItem('kept')").join(), "another directory: none of it");
            assertTrue(Files.list(profile).findAny().isPresent(), "written to disk there");
        }
        assertThrows(IllegalArgumentException.class, () -> WindowOptions.blank().dataDirectory(Path.of("relative")));
        delete(dir);
    }

    /** Deletes a directory once nothing holds its files any more (up to 10 seconds; what is left, target/ cleans). */
    static void delete(Path dir) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            try (var paths = Files.walk(dir)) {
                for (Path p : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
                return;
            } catch (java.io.IOException | java.io.UncheckedIOException busy) {
                Thread.sleep(200);
            }
        }
    }

    @Test
    void framesResizingIconsAndOffscreenAreWhatWindowsShows() throws Exception {
        try (App app = App.start()) {
            app.open(WindowOptions.html("<p>a</p>").title("swaswa test: framed").size(400, 300)).join();
            app.open(WindowOptions.html("<p>b</p>").title("swaswa test: frameless").size(400, 300).decorations(false)).join();
            assertTrue(Win32.frameHeight(Win32.window("swaswa test: framed")) > 20, "a title bar");
            assertTrue(Win32.frameHeight(Win32.window("swaswa test: frameless")) < 10, "decorations(false): none");

            app.open(WindowOptions.html("<p>c</p>").title("swaswa test: fixed").size(400, 300).resizable(false)).join();
            assertEquals(0L, Win32.style(Win32.window("swaswa test: fixed")) & Win32.WS_THICKFRAME, "resizable(false): no sizing border");
            assertNotEquals(0L, Win32.style(Win32.window("swaswa test: framed")) & Win32.WS_THICKFRAME, "resizable by default");

            app.open(WindowOptions.html("<p>d</p>").title("swaswa test: icon").size(400, 300).icon(png(32))).join();
            assertNotEquals(0L, Win32.icon(Win32.window("swaswa test: icon")), "icon: the window's own");
            assertEquals(0L, Win32.icon(Win32.window("swaswa test: framed")), "none given: none of its own");
            assertThrows(IllegalArgumentException.class, () -> WindowOptions.blank().icon(new byte[0]), "an empty icon");

            // offscreen: drawn, but for Java: the mouse goes through it, and it is all but invisible
            View off = app.open(WindowOptions.html("<p id=p>off</p>").title("swaswa test: offscreen").size(400, 300).offscreen()).join();
            MemorySegment hwnd = Win32.window("swaswa test: offscreen");
            long ex = until(() -> Win32.exStyle(hwnd), s -> (s & Win32.WS_EX_LAYERED) != 0);
            assertNotEquals(0L, ex & Win32.WS_EX_LAYERED, "layered (see-through)");
            assertNotEquals(0L, ex & WS_EX_TRANSPARENT, "the mouse goes through");
            assertEquals("off", until(() -> off.eval("document.getElementById('p')?.textContent ?? null").join(), "off"::equals), "its page runs");
            assertEquals("PNG", new String(off.screenshot().join(), 1, 3), "and is drawn: Java can take its picture");
        }
    }

    private static final long WS_EX_TRANSPARENT = 0x00000020L;

    /** A small PNG of one colour, made here: its signature, header, pixels (zlib) and end. */
    static byte[] png(int size) {
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        for (int y = 0; y < size; y++) {
            raw.write(0);   // no filter
            for (int x = 0; x < size; x++) raw.writeBytes(new byte[] {0x31, 0x57, (byte) 0xD5, (byte) 0xFF});
        }
        Deflater deflater = new Deflater();
        deflater.setInput(raw.toByteArray());
        deflater.finish();
        byte[] buffer = new byte[64 * 1024];
        int length = deflater.deflate(buffer);
        deflater.end();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10});
        chunk(out, "IHDR", ByteBuffer.allocate(13).putInt(size).putInt(size).put((byte) 8).put((byte) 6).put((byte) 0).put((byte) 0).put((byte) 0).array());
        chunk(out, "IDAT", java.util.Arrays.copyOf(buffer, length));
        chunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private static void chunk(ByteArrayOutputStream out, String type, byte[] data) {
        byte[] name = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(name);
        crc.update(data);
        out.writeBytes(ByteBuffer.allocate(4).putInt(data.length).array());
        out.writeBytes(name);
        out.writeBytes(data);
        out.writeBytes(ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
    }
}
