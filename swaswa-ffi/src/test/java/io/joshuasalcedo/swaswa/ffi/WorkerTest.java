package io.joshuasalcedo.swaswa.ffi;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** Background work: what it makes, how far it says it is, cancelled when asked, and a failure with what to do. */
@EnabledOnOs(OS.WINDOWS)
@Timeout(60)
class WorkerTest {

    @Test
    void itMakesItsResultAndSaysHowFarItIs() throws Exception {
        try (App app = App.start()) {
            List<WorkerProgress> progress = new CopyOnWriteArrayList<>();
            BlockingQueue<WorkerFinished> finished = new LinkedBlockingQueue<>();
            app.subscribe(WorkerProgress.class, progress::add);
            app.subscribe(WorkerFinished.class, finished::add);
            View view = app.open(WindowOptions.html("<p>w</p>").title("swaswa test: worker").size(300, 200)).join();

            Worker<Integer> sum = app.worker("sum", p -> {
                int total = 0;
                for (int i = 1; i <= 100; i++) {
                    total += i;
                    p.report(i / 100.0, "adding " + i);
                    Thread.sleep(5);
                }
                return total;
            }).showProgressOn(view);

            assertEquals(5050, sum.result().get(15, TimeUnit.SECONDS));
            WorkerFinished done = finished.poll(5, TimeUnit.SECONDS);
            assertEquals(new WorkerFinished("sum", WorkerFinished.State.DONE, null, null), done);
            assertFalse(sum.running());

            assertFalse(progress.isEmpty());
            assertTrue(progress.size() < 100, "not one event per step: at most about 30 a second: " + progress.size());
            for (int i = 1; i < progress.size(); i++) assertTrue(progress.get(i).fraction() >= progress.get(i - 1).fraction(), "forward");
            assertEquals(new WorkerProgress("sum", 1.0, "adding 100"), progress.getLast(), "the complete one always comes");
            assertEquals(1.0, sum.fraction());
        }
    }

    @Test
    void cancelledItStops() throws Exception {
        try (App app = App.start()) {
            BlockingQueue<WorkerFinished> finished = new LinkedBlockingQueue<>();
            app.subscribe(WorkerFinished.class, finished::add);

            // work that checks
            Worker<Void> checking = app.worker("checking", p -> {
                while (true) {
                    p.report(-1, "working");
                    p.checkCancelled();
                    Thread.onSpinWait();
                }
            });
            Thread.sleep(200);
            checking.cancel();
            assertThrows(CancellationException.class, () -> checking.result().get(5, TimeUnit.SECONDS), "a cancelled future's get()");
            assertEquals(WorkerFinished.State.CANCELLED, finished.poll(5, TimeUnit.SECONDS).state());

            // work that waits: interrupted
            Worker<Void> waiting = app.worker("waiting", p -> {
                Thread.sleep(60_000);
                return null;
            });
            Thread.sleep(100);
            waiting.cancel();
            assertThrows(CancellationException.class, () -> waiting.result().get(5, TimeUnit.SECONDS));
            assertEquals(new WorkerFinished("waiting", WorkerFinished.State.CANCELLED, null, null), finished.poll(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void aFailureSaysWhyAndWhatToDo() throws Exception {
        try (App app = App.start()) {
            BlockingQueue<WorkerFinished> finished = new LinkedBlockingQueue<>();
            app.subscribe(WorkerFinished.class, finished::add);
            View view = app.open(WindowOptions.html("<p>w</p>").title("swaswa test: failing").size(300, 200)).join();

            Worker<String> failing = app.<String>worker("import", p -> {
                p.report(0.3, "reading");
                throw new SystemException("the file is not there", "Choose the file again.", null, null);
            }).showProgressOn(view);
            ExecutionException why = assertThrows(ExecutionException.class, () -> failing.result().get(5, TimeUnit.SECONDS));
            assertInstanceOf(SystemException.class, why.getCause(), "the work's own exception");
            assertEquals(new WorkerFinished("import", WorkerFinished.State.FAILED, "the file is not there", "Choose the file again."),
                    finished.poll(5, TimeUnit.SECONDS));
        }
    }
}
