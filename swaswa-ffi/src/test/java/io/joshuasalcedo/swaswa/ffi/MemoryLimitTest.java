package io.joshuasalcedo.swaswa.ffi;

import static io.joshuasalcedo.swaswa.ffi.ExceptionsTest.failure;
import static io.joshuasalcedo.swaswa.ffi.ViewMethodsTest.until;
import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * The hard memory limit: past it, what would take more is refused (saying what uses the most) and what runs goes on;
 * enforced, the system holds the engine to it. And an engine process that fails is reported, not a blank page.
 */
@EnabledOnOs(OS.WINDOWS)
@Timeout(120)
class MemoryLimitTest {

    @Test
    void pastTheLimitWhatWouldTakeMoreIsRefused() throws Exception {
        try (App app = App.start()) {
            View view = app.open(WindowOptions.html("<p>m</p>").headless(true)).join();
            app.memoryLimit(1);   // any app is past 1 byte

            ResourceLimitException open = failure(ResourceLimitException.class, app.open(WindowOptions.html("<p>more</p>").headless(true)));
            assertTrue(open.reason().contains("could not open another window"), open.reason());
            assertTrue(open.hint().contains("process (pid "), "says what uses the most: " + open.hint());
            assertEquals(1, open.limit());
            assertTrue(open.memory().total() > 1);

            ResourceLimitException embed = failure(ResourceLimitException.class, view.embed(WindowOptions.html("<p>in</p>"), 0, 0, 50, 50));
            assertTrue(embed.reason().contains("embed another view"), embed.reason());

            assertEquals("m", until(() -> view.eval("document.body?.textContent ?? null").join(), "m"::equals), "what runs goes on");

            app.memoryLimit(0);
            assertNotNull(app.open(WindowOptions.html("<p>now</p>").headless(true)).join(), "no limit: opened");
        }
    }

    @Test
    void aRendererThatGoesIsReportedAndItsPageComesBack() throws Exception {
        try (App app = App.start()) {
            BlockingQueue<EngineProcessFailed> failed = new LinkedBlockingQueue<>();
            app.subscribe(EngineProcessFailed.class, failed::add);
            View view = app.open(WindowOptions.html("<p id=p>alive</p>").headless(true)).join();
            until(() -> view.eval("document.getElementById('p')?.textContent ?? null").join(), "alive"::equals);

            for (Memory.Process renderer : app.memory().join().of("renderer")) {
                ProcessHandle.of(renderer.pid()).ifPresent(ProcessHandle::destroyForcibly);
            }
            EngineProcessFailed gone = failed.poll(20, TimeUnit.SECONDS);
            assertNotNull(gone, "the renderer's end reported");
            assertEquals(view.id(), gone.view());
            assertTrue(gone.kind().equals("renderer") || gone.kind().equals("frame-renderer"), gone.toString());

            view.reload().join();
            assertEquals("alive", until(() -> {
                try {
                    return view.eval("document.getElementById('p')?.textContent ?? null").get(3, TimeUnit.SECONDS);
                } catch (Exception notYet) {
                    return null;
                }
            }, "alive"::equals), "reloaded: the page is back");
        }
    }

    @Test
    void uncappedTheSamePageTakesFarMore() throws Exception {
        try (App app = App.start()) {
            View view = app.open(WindowOptions.html("<p id=p>hungry</p>").headless(true)).join();
            until(() -> view.eval("document.getElementById('p')?.textContent ?? null").join(), "hungry"::equals);
            // the control for the test below: no cap, the page takes up to 1.2 GB (where it stops itself)
            view.eval("window.__got = 0; window.__hog = []; (function eat() { try { if (window.__got >= 1200) return; window.__hog.push(new Uint8Array(16 * 1024 * 1024).fill(1)); window.__got += 16; setTimeout(eat, 5); } catch (e) { window.__refused = String(e); } })()");
            long got = until(() -> ((Number) view.eval("window.__got").join()).longValue(), n -> n >= 1200);
            assertTrue(got >= 1200, "uncapped, the page got " + got + " MB");
        }
    }

    /** Started in its own JVM by the test below: a process can never leave the limit once held to it. */
    static final class LimitedApp {
        public static void main(String[] args) throws Exception {
            boolean held = App.enforceMemoryLimit(Long.parseLong(args[0]) * 1024 * 1024);
            System.out.println("held " + held);
            try (App app = App.start()) {
                java.util.concurrent.atomic.AtomicReference<String> failed = new java.util.concurrent.atomic.AtomicReference<>();
                app.subscribe(EngineProcessFailed.class, e -> failed.set(e.kind() + " " + e.reason()));
                View view = app.open(WindowOptions.html("<p id=p>hungry</p>").headless(true)).join();
                until(() -> view.eval("document.getElementById('p')?.textContent ?? null").join(), "hungry"::equals);
                view.eval("window.__got = 0; window.__hog = []; (function eat() { try { if (window.__got >= 3000) return; window.__hog.push(new Uint8Array(16 * 1024 * 1024).fill(1)); window.__got += 16; setTimeout(eat, 5); } catch (e) { window.__refused = String(e); } })()");
                long until = System.currentTimeMillis() + 60_000;
                while (System.currentTimeMillis() < until) {
                    if (failed.get() != null) { System.out.println("failed " + failed.get()); break; }
                    try {
                        Object refused = view.eval("window.__refused ?? null").get(3, TimeUnit.SECONDS);
                        long got = ((Number) view.eval("window.__got").get(3, TimeUnit.SECONDS)).longValue();
                        if (refused != null || got >= 3000) { System.out.println("got " + got + " refused " + refused); break; }
                    } catch (Exception rendererGone) {
                        // its renderer failed: the event says
                    }
                    Thread.sleep(200);
                }
                System.out.flush();
            }
            Runtime.getRuntime().halt(0);
        }
    }

    @Test
    void enforcedTheSystemHoldsTheWholeAppToIt() throws Exception {
        String javaExe = java.nio.file.Path.of(System.getProperty("java.home"), "bin", "java.exe").toString();
        Process limited = new ProcessBuilder(javaExe, "-Xmx256m", "--enable-native-access=ALL-UNNAMED",
                "-Dswaswa.webview.data=" + java.nio.file.Path.of("target", "webview-profile", "limited-" + System.nanoTime()).toAbsolutePath(),
                "-cp", System.getProperty("java.class.path"), LimitedApp.class.getName(), "1200").redirectErrorStream(true).start();
        StringBuilder said = new StringBuilder();
        try (var out = new java.io.BufferedReader(new java.io.InputStreamReader(limited.getInputStream()))) {
            for (String line; (line = out.readLine()) != null; ) said.append(line).append(System.lineSeparator());
        }
        limited.waitFor(90, TimeUnit.SECONDS);
        String text = said.toString();
        System.out.println("memory limit: " + text.lines().filter(l -> l.startsWith("held") || l.startsWith("got") || l.startsWith("failed")).toList());
        assertTrue(text.contains("held true"), "the system holds it: " + text);
        java.util.regex.Matcher got = java.util.regex.Pattern.compile("got ([0-9]+) refused (.+)").matcher(text);
        if (got.find()) {
            assertTrue(Long.parseLong(got.group(1)) < 1100, "held under 1.2 GB with Java: the page got " + got.group(1) + " MB, not 3 GB");
            assertTrue(got.group(2).contains("allocation failed"), "refused by the system: " + got.group(2));
        } else {
            assertTrue(text.contains("failed renderer") || text.contains("failed frame-renderer"), "or its renderer failed: " + text);
        }
    }
}
