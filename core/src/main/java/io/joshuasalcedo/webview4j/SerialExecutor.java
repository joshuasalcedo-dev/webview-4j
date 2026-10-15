package io.joshuasalcedo.webview4j;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/** Runs its tasks one at a time, in order, on the delegate: one subscriber's events. */
final class SerialExecutor implements Executor {

    private final Executor delegate;
    private final Queue<Runnable> tasks = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean scheduled = new AtomicBoolean();

    SerialExecutor(Executor delegate) {
        this.delegate = delegate;
    }

    @Override
    public void execute(Runnable task) {
        tasks.add(task);
        schedule();
    }

    private void schedule() {
        if (scheduled.compareAndSet(false, true)) delegate.execute(this::drain);
    }

    private void drain() {
        try {
            Runnable task;
            while ((task = tasks.poll()) != null) task.run();
        } finally {
            scheduled.set(false);
            if (!tasks.isEmpty()) schedule();   // a task arrived after the last poll
        }
    }
}
