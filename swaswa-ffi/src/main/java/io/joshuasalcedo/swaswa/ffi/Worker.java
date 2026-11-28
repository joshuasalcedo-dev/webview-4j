package io.joshuasalcedo.swaswa.ffi;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Work the app does in the background ({@link App#worker(String, Worker.Work)}), on a virtual thread: it says how far it
 * is ({@link WorkerProgress}), can be cancelled, and ends once ({@link WorkerFinished}: done, cancelled or failed, a
 * failure with its exception's hint). {@link #showProgressOn(View)} puts its progress on a window's taskbar button.
 *
 * @param <T> what it makes
 */
public final class Worker<T> {

    /** The work: what it makes, saying how far it is on {@code progress}, and stopping when cancelled. */
    @FunctionalInterface
    public interface Work<T> {
        T run(Progress progress) throws Exception;
    }

    /** What the work says and asks while it runs. */
    public interface Progress {
        /** How far it is: 0 to 1, or a negative number when it cannot say (an indeterminate bar); and what it does. */
        void report(double fraction, String message);

        /** Whether it was asked to stop. */
        boolean cancelled();

        /** Stops the work here if it was asked to: throws a CancellationException. */
        default void checkCancelled() {
            if (cancelled()) throw new CancellationException("cancelled");
        }
    }

    private final String name;
    private final CompletableFuture<T> result = new CompletableFuture<>();
    private volatile boolean cancelled;
    private volatile Thread thread;
    private volatile double fraction = -1;
    private volatile String message = "";
    private volatile View shownOn;
    private final Consumer<WebViewEvent> publish;
    private long lastReport;

    Worker(String name, Work<T> work, Consumer<WebViewEvent> publish) {
        this.name = Objects.requireNonNull(name, "name");
        this.publish = publish;
        thread = Thread.ofVirtual().name("swaswa-worker-" + name).start(() -> run(work));
    }

    private void run(Work<T> work) {
        Progress progress = new Progress() {
            @Override
            public void report(double f, String m) {
                fraction = f < 0 ? -1 : Math.min(1, f);
                message = m == null ? "" : m;
                long now = System.nanoTime();
                // at most about 30 a second, and the last one (complete) always
                if (fraction >= 1 || now - lastReport > 33_000_000L) {
                    lastReport = now;
                    publish.accept(new WorkerProgress(name, fraction, message));
                    View view = shownOn;
                    if (view != null) {
                        view.setProgress(fraction < 0 ? View.Progress.INDETERMINATE : View.Progress.NORMAL, (int) Math.round(Math.max(0, fraction) * 100));
                    }
                }
            }

            @Override
            public boolean cancelled() {
                return cancelled;
            }
        };
        try {
            T made = work.run(progress);
            if (cancelled) throw new CancellationException("cancelled");
            finish(WorkerFinished.State.DONE, null);
            result.complete(made);
        } catch (CancellationException | InterruptedException stopped) {
            finish(WorkerFinished.State.CANCELLED, null);
            result.completeExceptionally(stopped instanceof CancellationException c ? c : new CancellationException("cancelled"));
        } catch (Throwable failed) {
            finish(WorkerFinished.State.FAILED, failed);
            result.completeExceptionally(failed);
        }
    }

    private void finish(WorkerFinished.State state, Throwable failed) {
        View view = shownOn;
        if (view != null) {
            view.setProgress(state == WorkerFinished.State.FAILED ? View.Progress.ERROR : View.Progress.NONE, 100);
            if (state == WorkerFinished.State.FAILED) {
                // the red bar a moment, then gone
                java.util.concurrent.CompletableFuture.delayedExecutor(3, java.util.concurrent.TimeUnit.SECONDS)
                        .execute(() -> view.setProgress(View.Progress.NONE, 0));
            }
        }
        String why = failed == null ? null : failed instanceof WebViewException w ? w.reason()
                : failed.getMessage() != null ? failed.getMessage() : failed.getClass().getSimpleName();
        String hint = failed instanceof WebViewException w ? w.hint() : null;
        publish.accept(new WorkerFinished(name, state, why, hint));
    }

    public String name() {
        return name;
    }

    /** Asks it to stop: the work sees it at its next {@link Progress#checkCancelled()}, or is interrupted waiting. */
    public Worker<T> cancel() {
        cancelled = true;
        Thread t = thread;
        if (t != null && !result.isDone()) t.interrupt();
        return this;
    }

    /** What it made; fails with its failure, or a CancellationException when it was cancelled. */
    public CompletableFuture<T> result() {
        return result;
    }

    public boolean running() {
        return !result.isDone();
    }

    /** How far it last said it was: 0 to 1, or -1 if it did not say. */
    public double fraction() {
        return fraction;
    }

    public String message() {
        return message;
    }

    /** Shows its progress on {@code view}'s taskbar button while it runs (red a moment if it fails). */
    public Worker<T> showProgressOn(View view) {
        this.shownOn = Objects.requireNonNull(view, "view");
        view.setProgress(fraction < 0 ? View.Progress.INDETERMINATE : View.Progress.NORMAL, (int) Math.round(Math.max(0, fraction) * 100));
        return this;
    }
}
