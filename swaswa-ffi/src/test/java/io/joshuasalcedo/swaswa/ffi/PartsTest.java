package io.joshuasalcedo.swaswa.ffi;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** The parts around the windows: grammar, the stores, a page's arguments, JSON, logs, the event order, recordings. */
@Timeout(120)
class PartsTest {

    @Test
    void grammarFindsMistakesAndFixesThem() {
        Grammar english = Grammar.english();
        assertSame(english, Grammar.of("en-US"), "made once");
        assertEquals("en-US", english.dialect());
        assertEquals("en-US", Grammar.of(null).dialect(), "no dialect: American");
        assertEquals("en-GB", Grammar.of("en-GB").dialect());

        String text = "This are a sentence with a speling mistake.";
        List<Grammar.Mistake> mistakes = english.check(text);
        Grammar.Mistake spelling = mistakes.stream().filter(Grammar.Mistake::spelling).findFirst().orElseThrow();
        assertEquals("speling", spelling.found(text), "where it is");
        assertTrue(spelling.suggestions().contains("spelling"), spelling.toString());
        // the grammar: a verb that does not agree, and "a" before a vowel
        List<Grammar.Mistake> grammar = english.check("I has a apple.");
        assertEquals(List.of("has", "a"), grammar.stream().map(m -> m.found("I has a apple.")).toList(), grammar.toString());
        assertFalse(grammar.get(0).spelling());
        assertEquals("I have an apple.", english.fix("I has a apple."), "each mistake fixed with its first suggestion");

        assertEquals("This are a sentence with a spelling mistake.", Grammar.apply(text, spelling, "spelling"), "one fixed");
        String fixed = english.fix(text);
        assertTrue(fixed.contains("spelling") && !fixed.contains("speling"), fixed);
        assertEquals(List.of(), english.check(fixed).stream().filter(Grammar.Mistake::spelling).toList(), "no spelling left: " + fixed);
        assertEquals(List.of(), english.check("This is a sentence."), "nothing to find");
    }

    @Test
    void aStoreKeepsDeletesAndListsItsKeys(@TempDir Path dir) {
        try (NativeStore store = NativeStore.open(dir.resolve("state.redb"))) {
            store.put("user.1", "Ada".getBytes(StandardCharsets.UTF_8));
            store.put("user.2", "Grace".getBytes(StandardCharsets.UTF_8));
            store.put("setting.theme", "dark".getBytes(StandardCharsets.UTF_8));
            assertEquals(List.of("user.1", "user.2"), store.keys("user."), "keys by prefix");
            assertEquals(3, store.keys("").size());
            assertTrue(store.delete("user.1"));
            assertFalse(store.delete("user.1"), "gone already");
            assertTrue(store.get("user.1").isEmpty());
            assertEquals(List.of("user.2"), store.keys("user."));
        }
        String app = "swaswa-test-" + UUID.randomUUID();
        Path folder = NativeStore.directory(app);
        try (NativeStore forApp = NativeStore.forApp(app)) {
            assertEquals(folder.resolve("state.redb"), forApp.file(), "the system's place for the app's data");
            forApp.put("k", new byte[] {1});
            assertArrayEquals(new byte[] {1}, forApp.get("k").orElseThrow());
        } finally {
            try (var files = Files.walk(folder)) {
                for (Path p : files.sorted(Collections.reverseOrder()).toList()) Files.deleteIfExists(p);
            } catch (java.io.IOException ignored) {
                // left for the next clean
            }
        }
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void aSecretIsKeptAndDeleted() {
        SecretStore secrets = SecretStore.of("swaswa-test-" + UUID.randomUUID());
        secrets.put("ada", "s3cret");
        assertEquals("s3cret", secrets.get("ada").orElseThrow());
        assertTrue(secrets.delete("ada"));
        assertTrue(secrets.get("ada").isEmpty(), "deleted");
        assertFalse(secrets.delete("ada"), "nothing left to delete");
    }

    @Test
    void aPagesArgumentsAreReadByType() throws Exception {
        try (App app = App.start()) {
            View view = app.open(WindowOptions.html("<p>i</p>").headless(true)).join();
            Map<String, Object> args = new java.util.HashMap<>(Map.of("name", "Ada", "age", 36L, "height", 1.7, "admin", true,
                    "tags", List.of("a", "b")));
            args.put("nothing", null);
            Invocation call = new Invocation(view, "save", args);
            assertEquals(args, call.fields());
            assertEquals(1.7, call.decimal("height"));
            assertEquals(36.0, call.decimal("age"), "a whole number is a number too");
            assertTrue(call.flag("admin"));
            assertEquals(List.of("a", "b"), call.list("tags"));
            assertTrue(call.has("nothing"), "passed, as null");
            assertFalse(call.has("missing"));
            assertThrows(IllegalArgumentException.class, () -> call.flag("name"));
            assertThrows(IllegalArgumentException.class, () -> call.list("name"));
            assertThrows(IllegalArgumentException.class, () -> call.decimal("name"));
            assertEquals(Map.of(), new Invocation(view, "none", "not an object").fields());
        }
    }

    record Person(String name, Integer age, String nickname) {}

    @Test
    void jsonLogLevelsAndMetrics() throws Exception {
        assertEquals(Map.of("name", "Ada", "age", 36), Json.withoutNulls(new Person("Ada", 36, null)));
        assertEquals(List.of("name", "age", "nickname"), List.copyOf(Json.withoutNulls(new Person("Ada", 36, "A")).keySet()), "in order");

        assertEquals(System.Logger.Level.ERROR, LogRecord.Level.ERROR.toSystemLevel());
        assertEquals(System.Logger.Level.WARNING, LogRecord.Level.WARN.toSystemLevel());
        assertEquals(System.Logger.Level.INFO, LogRecord.Level.INFO.toSystemLevel());
        assertEquals(System.Logger.Level.DEBUG, LogRecord.Level.DEBUG.toSystemLevel());
        assertEquals(System.Logger.Level.TRACE, LogRecord.Level.TRACE.toSystemLevel());

        try (App app = App.start()) {
            View view = app.open(WindowOptions.html("<p>m</p>").headless(true)).join();
            view.eval("1").join();
            Metrics metrics = app.metrics();
            assertTrue(metrics.byAction().stream().anyMatch(t -> t.count() > 0 && t.maxMillis() >= t.averageMillis() && t.p95Millis() <= t.maxMillis()),
                    "each action timed: " + metrics.byAction());
        }
    }

    @Test
    void oneSubscribersEventsRunOneAtATimeInOrder() throws Exception {
        var pool = Executors.newFixedThreadPool(8);
        try {
            SerialExecutor serial = new SerialExecutor(pool);
            List<Integer> seen = Collections.synchronizedList(new ArrayList<>());
            java.util.concurrent.atomic.AtomicInteger running = new java.util.concurrent.atomic.AtomicInteger();
            java.util.concurrent.atomic.AtomicInteger most = new java.util.concurrent.atomic.AtomicInteger();
            CountDownLatch done = new CountDownLatch(500);
            for (int i = 0; i < 500; i++) {
                int n = i;
                serial.execute(() -> {
                    most.accumulateAndGet(running.incrementAndGet(), Math::max);
                    seen.add(n);
                    running.decrementAndGet();
                    done.countDown();
                });
            }
            assertTrue(done.await(10, TimeUnit.SECONDS));
            assertEquals(1, most.get(), "never two at once");
            for (int i = 0; i < 500; i++) assertEquals(i, seen.get(i), "in order");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void aRecordingStopsAtItsLimitAndWebmNeedsFfmpeg(@TempDir Path dir) throws Exception {
        try (App app = App.start()) {
            View view = app.open(WindowOptions.html("<body style='background:#3157d5'>r</body>").size(320, 240).title("swaswa test: recorded")).join();
            Thread.sleep(600);

            Recording limited = view.record(dir.resolve("limited.gif"), Recording.Options.gif().fps(10).maxSeconds(1)).join();
            Thread.sleep(2500);
            // it stopped by itself at the limit, its file finished; stop() still hands back what it recorded
            assertTrue(Files.size(limited.file()) > 100, "finished and written at the limit");
            Recording.Result result = limited.stop().get(30, TimeUnit.SECONDS);
            assertEquals(1.0, result.seconds(), 0.3, "one second, not the 2.5 waited: " + result);
            assertTrue(result.frames() >= 8 && result.frames() <= 12, "about ten frames: " + result);

            Recording.Options webm = Recording.Options.webm().fps(15).maxSeconds(5);
            assertEquals(Recording.Format.WEBM, webm.format());
            assertEquals(5, webm.maxSeconds());
            if (onPath("ffmpeg")) {
                Recording video = view.record(dir.resolve("clip.webm"), webm).join();
                Thread.sleep(1200);
                Recording.Result clip = video.stop().get(60, TimeUnit.SECONDS);
                byte[] head = Files.readAllBytes(clip.file());
                assertEquals(0x1A45DFA3, java.nio.ByteBuffer.wrap(head, 0, 4).getInt(), "a WebM (EBML) file");
                assertTrue(clip.frames() > 5, clip.toString());
            } else {
                var failed = assertThrows(java.util.concurrent.CompletionException.class,
                        () -> view.record(dir.resolve("clip.webm"), webm).join());
                assertTrue(failed.getCause().getMessage().toLowerCase().contains("ffmpeg"), failed.getCause().getMessage());
            }
        }
    }

    private static boolean onPath(String program) {
        String path = System.getenv("PATH");
        if (path == null) return false;
        for (String dir : path.split(java.io.File.pathSeparator)) {
            if (Files.exists(Path.of(dir, program + ".exe")) || Files.exists(Path.of(dir, program))) return true;
        }
        return false;
    }
}
