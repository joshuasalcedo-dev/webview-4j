package io.joshuasalcedo.webview4j.plugin.automation;

import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.Json;
import io.joshuasalcedo.webview4j.Subscription;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What a person does in a window, written down as steps while they do it: clicks, double- and right-clicks by what
 * was clicked (its role and name), what was typed into which field (a password as {@code ${PASSWORD}}), keys, choices
 * in selects. {@link #stop()} gives the {@link Script}; save it, replay it, turn it into a test.
 * <pre>{@code
 * Recorder recorder = Automation.of(view).record();
 * ... // the person uses the window
 * recorder.stop().save(Path.of("docs/sign-in.automation.json"));
 * }</pre>
 */
public final class Recorder {

    static final String COMMAND = "webview4j.automation.step";
    private static final Map<App, Subscription> HANDLERS = new ConcurrentHashMap<>();
    private static final Map<String, Recorder> RECORDING = new ConcurrentHashMap<>();

    private final Automation automation;
    private final List<Script.Step> steps = new ArrayList<>();
    private final List<Long> times = new ArrayList<>();
    private volatile boolean paused;

    private Recorder(Automation automation) {
        this.automation = automation;
    }

    static Recorder start(Automation automation) {
        App app = automation.view().app();
        HANDLERS.computeIfAbsent(app, a -> a.handle(COMMAND, call -> {
            Recorder r = RECORDING.get(call.view().id());
            if (r != null && call.args() instanceof Map<?, ?> step) r.add(step);
            return null;
        }));
        Recorder recorder = new Recorder(automation);
        if (RECORDING.putIfAbsent(automation.view().id(), recorder) != null) {
            throw new IllegalStateException("this window is already being recorded");
        }
        automation.call("__w4jAuto.record(" + Json.write(COMMAND) + ")");
        return recorder;
    }

    /** Stops writing steps down for a while (what the person does meanwhile is not in the script). */
    public void pause() {
        paused = true;
    }

    public void resume() {
        paused = false;
    }

    /** The steps so far. */
    public synchronized List<Script.Step> steps() {
        return List.copyOf(steps);
    }

    /** Adds a step of your own (a check, a screenshot) where the recording is now. */
    public synchronized Recorder add(Script.Step step) {
        steps.add(step);
        times.add(System.nanoTime());
        return this;
    }

    synchronized void add(Map<?, ?> raw) {
        if (paused) return;
        Script.Step step;
        try {
            step = Script.read(raw);
        } catch (RuntimeException e) {
            return;   // a step the page made up wrong: left out
        }
        long now = System.nanoTime();
        if (!steps.isEmpty()) {
            Script.Step last = steps.getLast();
            boolean soon = now - times.getLast() < 700_000_000L;
            // a double-click comes after the click that started it: one step
            if (step instanceof Script.Step.DoubleClick d && last instanceof Script.Step.Click c && c.target().equals(d.target()) && soon) {
                steps.removeLast();
                times.removeLast();
            }
            // typing in the same field again: what it holds at the end
            if (step instanceof Script.Step.Type t && last instanceof Script.Step.Type l && l.target().equals(t.target())) {
                steps.removeLast();
                times.removeLast();
            }
        }
        steps.add(step);
        times.add(now);
    }

    /** Stops recording: the script, named {@code name}. */
    public Script stop(String name) {
        try {
            // The page writes down what is still being typed, and stops listening. When the window has closed
            // already there is no page to ask: every step it sent is here.
            automation.call("__w4jAuto.stopRecording()");
            Thread.sleep(150);   // the last steps on their way
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException closed) {
            // the window is gone (closing it is how a recording often ends)
        } finally {
            RECORDING.remove(automation.view().id(), this);
        }
        return new Script(name, steps());
    }

    public Script stop() {
        return stop("recorded " + java.time.LocalDate.now());
    }
}
