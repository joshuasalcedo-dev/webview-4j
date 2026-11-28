package io.joshuasalcedo.swaswa.ffi;

import static io.joshuasalcedo.swaswa.ffi.ViewMethodsTest.until;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * A canvas Java animates ({@link View#animate}): in step with the display in a shown window, still running in a
 * hidden one, given the time of each frame, and stopped by Java, by a failing painter, or by the window closing.
 */
@EnabledOnOs(OS.WINDOWS)
@Timeout(120)
class AnimationTest {

    static final int W = 320, H = 200;

    /** A frame of one shade: enough to see it changed. */
    static Frame shade(double seconds) {
        byte[] rgba = new byte[W * H * 4];
        byte v = (byte) (seconds * 200);
        for (int i = 0; i < rgba.length; i += 4) { rgba[i] = v; rgba[i + 1] = (byte) (255 - v); rgba[i + 3] = (byte) 255; }
        return Frame.rgba(W, H, rgba);
    }

    static View canvas(App app, boolean shown) throws InterruptedException {
        WindowOptions options = WindowOptions.html("<body style='margin:0'><canvas id=c></canvas>").size(400, 300).title("swaswa test: animated");
        View view = app.open(shown ? options : options.headless(true)).join();
        until(() -> view.eval("document.getElementById('c') !== null").join(), Boolean.TRUE::equals);
        return view;
    }

    @Test
    void inAShownWindowItRunsInStepWithTheDisplay() throws Exception {
        try (App app = App.start()) {
            View view = canvas(app, true);
            view.focus().join();
            // the display's rate, as the page sees it: its refreshes in one second
            view.eval("window.__refreshes = 0; (function tick() { window.__refreshes++; requestAnimationFrame(tick); })()").join();
            Thread.sleep(1000);
            long refreshes = ((Number) view.eval("window.__refreshes").join()).longValue();
            assertTrue(refreshes >= 30, "the window is shown and refreshing: " + refreshes + " a second");

            List<Double> times = new CopyOnWriteArrayList<>();
            Animation animation = view.animate("c", seconds -> {
                times.add(seconds);
                return shade(seconds);
            });
            Thread.sleep(1000);   // under way
            long refreshedBefore = ((Number) view.eval("window.__refreshes").join()).longValue(), framesBefore = animation.frames();
            Thread.sleep(1500);
            // the display's refreshes and the frames drawn over the same 1.5 seconds
            long shownRefreshes = ((Number) view.eval("window.__refreshes").join()).longValue() - refreshedBefore;
            long shownFrames = animation.frames() - framesBefore;
            double fps = animation.framesPerSecond();
            animation.stop();
            long frames = animation.whenStopped().get(10, TimeUnit.SECONDS);
            System.out.println("animation: " + Math.round(fps) + " frames a second; over 1.5 s, " + shownFrames + " frames for "
                    + shownRefreshes + " display refreshes");

            // in step: about one frame per refresh, never more (none drawn and thrown away)
            assertTrue(shownFrames <= shownRefreshes + 3, "no more frames than refreshes: " + shownFrames + " vs " + shownRefreshes);
            assertTrue(shownFrames >= shownRefreshes * 0.6, "close to one a refresh: " + shownFrames + " vs " + shownRefreshes);
            assertTrue(frames >= refreshes, "frames drawn: " + frames);

            // each frame painted for its time: increasing, spanning the time it ran
            for (int i = 1; i < times.size(); i++) assertTrue(times.get(i) > times.get(i - 1), "time goes forward");
            assertEquals(0, times.getFirst(), 0.05, "it starts at 0");
            assertEquals(2.5, times.getLast(), 0.4, "and ends about when it was stopped");

            // stopped: no more frames
            long after = animation.frames();
            Thread.sleep(300);
            assertEquals(after, animation.frames(), "stopped");
            assertFalse(animation.running());
        }
    }

    @Test
    void inAHiddenWindowItStillRuns() throws Exception {
        try (App app = App.start()) {
            View view = canvas(app, false);
            Animation animation = view.animate("c", AnimationTest::shade);
            Thread.sleep(1500);
            double fps = animation.framesPerSecond();
            animation.stop();
            animation.whenStopped().get(10, TimeUnit.SECONDS);
            assertTrue(fps >= 20, "no display refreshes to wait for: drawn as they come: " + fps);
        }
    }

    @Test
    void itStopsWhenThePainterFailsOrTheWindowCloses() throws Exception {
        try (App app = App.start()) {
            View view = canvas(app, false);
            Animation failing = view.animate("c", seconds -> {
                if (seconds > 0.3) throw new IllegalStateException("the painter gave up");
                return shade(seconds);
            });
            ExecutionException why = assertThrows(ExecutionException.class, () -> failing.whenStopped().get(10, TimeUnit.SECONDS));
            assertEquals("the painter gave up", why.getCause().getMessage());
            assertFalse(failing.running());

            Animation closing = view.animate("c", AnimationTest::shade);
            until(closing::frames, n -> n > 5);
            view.close().join();
            assertThrows(ExecutionException.class, () -> closing.whenStopped().get(10, TimeUnit.SECONDS), "the window closed: it stopped");

            View other = canvas(app, false);
            Animation nowhere = other.animate("missing", AnimationTest::shade);
            ExecutionException missing = assertThrows(ExecutionException.class, () -> nowhere.whenStopped().get(10, TimeUnit.SECONDS));
            assertTrue(missing.getCause().getMessage().contains("no <canvas"), missing.getCause().getMessage());
            assertThrows(IllegalArgumentException.class, () -> other.animate("bad name", AnimationTest::shade));
        } catch (CompletionException e) {
            throw e;
        }
    }
}
