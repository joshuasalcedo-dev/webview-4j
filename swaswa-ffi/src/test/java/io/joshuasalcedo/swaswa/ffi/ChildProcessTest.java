package io.joshuasalcedo.swaswa.ffi;

import static io.joshuasalcedo.swaswa.ffi.ViewMethodsTest.until;
import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * The app's child processes: their output as events, their input, their end, and their tie to the app's life, down
 * to an app that crashes (another JVM, ended without a word: its child must end with it).
 */
@EnabledOnOs(OS.WINDOWS)
@Timeout(120)
class ChildProcessTest {

    /** A process that runs a while and does nothing: ping, waiting a second between tries. */
    static final String[] LONG = {"ping", "-n", "120", "127.0.0.1"};

    @Test
    void itsLinesComeAsEventsInOrderThenItsEnd() throws Exception {
        try (App app = App.start()) {
            List<ProcessOutput> lines = new CopyOnWriteArrayList<>();
            BlockingQueue<ProcessExited> exits = new LinkedBlockingQueue<>();
            app.subscribe(ProcessOutput.class, lines::add);
            app.subscribe(ProcessExited.class, exits::add);

            ChildProcess child = app.process("echo", "cmd", "/c", "echo one& echo two& echo three& echo oops 1>&2& exit 7");
            assertTrue(child.tiedToApp(), "in the app's job");
            ProcessExited exited = exits.poll(15, TimeUnit.SECONDS);
            assertNotNull(exited, "it ended, and said so");
            assertEquals("echo", exited.process());
            assertEquals(child.pid(), exited.pid());
            assertEquals(7, exited.exitCode());
            assertEquals(7, child.onExit().join());

            List<String> output = lines.stream().filter(l -> l.stream() == ProcessOutput.Stream.OUTPUT).map(ProcessOutput::line).map(String::strip).toList();
            assertEquals(List.of("one", "two", "three"), output, "its output, in order, before its end");
            assertEquals(List.of("oops"), lines.stream().filter(l -> l.stream() == ProcessOutput.Stream.ERROR).map(l -> l.line().strip()).toList(), "its errors");
            assertTrue(app.children().isEmpty(), "ended: no longer a child");
        }
    }

    @Test
    void whatIsWrittenToItIsWhatItReads() throws Exception {
        try (App app = App.start()) {
            List<String> lines = new CopyOnWriteArrayList<>();
            app.subscribe(ProcessOutput.class, l -> lines.add(l.line().strip()));
            ChildProcess sort = app.process("sort", "sort");
            sort.write("pear\r\n").write("apple\r\n").write("fig\r\n").closeInput();
            assertEquals(0, sort.onExit().get(15, TimeUnit.SECONDS));
            assertEquals(List.of("apple", "fig", "pear"), until(() -> List.copyOf(lines), l -> l.size() >= 3), "sorted by it");
            assertThrows(SystemException.class, () -> sort.write("more\r\n"), "ended: nothing more to write to");
        }
    }

    @Test
    void itEndsWhenKilledOrWhenTheAppCloses() throws Exception {
        App app = App.start();
        ChildProcess killed = app.process("killed", LONG);
        assertTrue(killed.running());
        assertNotNull(killed.kill(Duration.ofSeconds(1)).get(10, TimeUnit.SECONDS));
        assertFalse(killed.running());

        // a child that started its own: both end with the app
        ChildProcess parent = app.process("parent", "cmd", "/c", String.join(" ", LONG));
        long grandchild = until(() -> parent.toString().isEmpty() ? -1L
                : ProcessHandle.of(parent.pid()).flatMap(h -> h.children().findFirst()).map(ProcessHandle::pid).orElse(-1L), pid -> pid > 0);
        assertTrue(grandchild > 0, "cmd started ping");
        assertEquals(List.of(parent), app.children());
        app.close();
        assertFalse(parent.running(), "the app closed: its child ended");
        assertTrue(until(() -> ProcessHandle.of(grandchild).map(ProcessHandle::isAlive).orElse(false), alive -> !alive) == Boolean.FALSE,
                "and what it started");
    }

    @Test
    void aProgramThatIsNotThereSaysSo() throws Exception {
        try (App app = App.start()) {
            SystemException missing = assertThrows(SystemException.class, () -> app.process("none", "no-such-program-swaswa"));
            assertTrue(missing.hint().contains("PATH"), missing.hint());
        }
    }

    /** Started in its own JVM by the test below: an app with a child, then a crash (no close, no shutdown hooks). */
    static final class CrashingApp {
        public static void main(String[] args) throws Exception {
            App app = App.start();
            ChildProcess child = app.process("orphan?", LONG);
            System.out.println("child " + child.pid() + " tied " + child.tiedToApp());
            System.out.flush();
            Thread.sleep(500);
            Runtime.getRuntime().halt(3);   // as a crash would: nothing runs after
        }
    }

    @Test
    void anAppThatCrashesTakesItsChildrenWithIt() throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java.exe").toString();
        ProcessBuilder builder = new ProcessBuilder(java, "--enable-native-access=ALL-UNNAMED",
                "-Dswaswa.webview.data=" + Path.of("target", "webview-profile", "crashing").toAbsolutePath(),
                "-cp", System.getProperty("java.class.path"), CrashingApp.class.getName()).redirectErrorStream(true);
        Process crashing = builder.start();
        long child = -1;
        String said = "";
        try (BufferedReader out = new BufferedReader(new InputStreamReader(crashing.getInputStream()))) {
            for (String line; (line = out.readLine()) != null; ) {
                said += line + "\n";
                if (line.startsWith("child ")) {
                    child = Long.parseLong(line.split(" ")[1]);
                    assertTrue(line.endsWith("tied true"), line);
                }
            }
        }
        assertEquals(3, crashing.waitFor(), "it crashed: " + said);
        assertTrue(child > 0, "it started its child: " + said);
        long pid = child;
        assertFalse(until(() -> ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false), alive -> !alive),
                "the app crashed, and the system ended its child with it");
    }
}
