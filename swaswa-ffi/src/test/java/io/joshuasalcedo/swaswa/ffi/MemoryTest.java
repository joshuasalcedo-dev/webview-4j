package io.joshuasalcedo.swaswa.ffi;

import static io.joshuasalcedo.swaswa.ffi.ViewMethodsTest.until;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * The app's memory: this process and the browser engine's, as Windows has them; a view's memory level; and a budget
 * that says when it is gone over and when it is back under, lowering the windows not shown meanwhile.
 */
@EnabledOnOs(OS.WINDOWS)
@Timeout(120)
class MemoryTest {

    @Test
    void theAppsMemoryIsWhatTheSystemSays() throws Exception {
        try (App app = App.start()) {
            View view = app.open(WindowOptions.html("<p>m</p>").headless(true)).join();
            until(() -> view.eval("document.body?.textContent ?? null").join(), "m"::equals);
            Memory memory = app.memory().join();

            assertEquals(ProcessHandle.current().pid(), memory.host().pid(), "this process first");
            assertTrue(memory.host().privateBytes() > 10_000_000, "this process: a JVM and the library, tens of MB: " + memory.host());
            assertTrue(memory.heapUsed() > 0 && memory.heapUsed() <= memory.heapMax(), memory.heapUsed() + " of " + memory.heapMax());
            assertEquals(Runtime.getRuntime().maxMemory(), memory.heapMax(), "-Xmx");

            assertEquals(1, memory.of("browser").size(), "the engine's browser process: " + memory.processes());
            assertFalse(memory.of("renderer").isEmpty(), "a renderer for the page: " + memory.processes());
            for (Memory.Process p : memory.processes()) {
                assertTrue(p.resident() > 0 && p.privateBytes() > 0, "each in memory: " + p);
                assertTrue(ProcessHandle.of(p.pid()).isPresent(), "a real process: " + p);
            }
            // the engine's processes are msedgewebview2.exe, as the system names them
            long browser = memory.of("browser").getFirst().pid();
            String command = ProcessHandle.of(browser).flatMap(h -> h.info().command()).orElse("");
            assertTrue(command.toLowerCase().endsWith("msedgewebview2.exe"), "WebView2's browser process: " + command);

            assertEquals(memory.processes().stream().mapToLong(Memory.Process::privateBytes).sum(), memory.total());
            assertEquals(memory.total() - memory.host().privateBytes(), memory.engine());
            assertTrue(memory.engine() > memory.host().privateBytes() / 10, "the engine is a good part of it: " + memory.engine());
        }
    }

    @Test
    void aViewCanBeAskedToUseLess() throws Exception {
        try (App app = App.start()) {
            View view = app.open(WindowOptions.html("<p>m</p>").headless(true)).join();
            assertEquals(View.MemoryLevel.NORMAL, view.memoryLevel().join(), "normal at first");
            view.setMemoryLevel(View.MemoryLevel.LOW).join();
            assertEquals(View.MemoryLevel.LOW, view.memoryLevel().join());
            view.setMemoryLevel(View.MemoryLevel.NORMAL).join();
            assertEquals(View.MemoryLevel.NORMAL, view.memoryLevel().join());

            View embedded = view.embed(WindowOptions.html("<p>in</p>"), 0, 0, 100, 100).join();
            embedded.setMemoryLevel(View.MemoryLevel.LOW).join();
            assertEquals(View.MemoryLevel.LOW, embedded.memoryLevel().join(), "an embedded view has its own");
        }
    }

    @Test
    void overTheBudgetItSaysSoAndHiddenWindowsUseLess() throws Exception {
        try (App app = App.start()) {
            BlockingQueue<MemoryPressure> pressure = new LinkedBlockingQueue<>();
            app.subscribe(MemoryPressure.class, pressure::add);
            View shown = app.open(WindowOptions.html("<p>shown</p>").title("swaswa test: shown").size(300, 200)).join();
            View hidden = app.open(WindowOptions.html("<p>hidden</p>").headless(true)).join();

            app.memoryBudget(1, Duration.ofMillis(300));   // any app is over 1 byte
            MemoryPressure over = pressure.poll(15, TimeUnit.SECONDS);
            assertNotNull(over, "told it is over");
            assertTrue(over.over());
            assertEquals(1, over.budget());
            assertTrue(over.memory().total() > 1);
            assertEquals(View.MemoryLevel.LOW, until(() -> hidden.memoryLevel().join(), View.MemoryLevel.LOW::equals), "the hidden window uses less");
            assertEquals(View.MemoryLevel.NORMAL, shown.memoryLevel().join(), "the shown one is left alone");
            assertNull(pressure.poll(1500, TimeUnit.MILLISECONDS), "once, not every check");

            app.memoryBudget(Long.MAX_VALUE / 2, Duration.ofMillis(300));   // and back under
            MemoryPressure under = pressure.poll(15, TimeUnit.SECONDS);
            assertNotNull(under, "told it is back under");
            assertFalse(under.over());
            assertEquals(View.MemoryLevel.NORMAL, hidden.memoryLevel().join(), "under it again: the hidden window back to normal");

            app.memoryBudget(0);   // no budget: no more checks, and nothing to say (it was under)
            assertNull(pressure.poll(1500, TimeUnit.MILLISECONDS));

            // over, then the budget taken away: back under, said once
            app.memoryBudget(1, Duration.ofMillis(300));
            assertTrue(pressure.poll(15, TimeUnit.SECONDS).over());
            app.memoryBudget(0);
            MemoryPressure removed = pressure.poll(15, TimeUnit.SECONDS);
            assertNotNull(removed, "taken away while over: said it is no longer over");
            assertFalse(removed.over());
            assertEquals(View.MemoryLevel.NORMAL, until(() -> hidden.memoryLevel().join(), View.MemoryLevel.NORMAL::equals));
        }
    }
}
