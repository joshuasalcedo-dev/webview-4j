package io.joshuasalcedo.webview4j.plugin.autoupdate;

import static org.junit.jupiter.api.Assertions.*;

import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.View;
import io.joshuasalcedo.webview4j.ViewInfo;
import io.joshuasalcedo.webview4j.ui.UiWindow;
import io.joshuasalcedo.webview4j.ui.state.StateStorage;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** What's new after an update, and the update prompt from the check to the installer, in real windows. */
@EnabledOnOs(OS.WINDOWS)
class AutoUpdateWindowTest extends ReleaseServer {

    @TempDir
    Path downloads;

    static Optional<View> window(App app, String title) {
        for (ViewInfo v : app.views().join()) if (v.title().equals(title)) return Optional.of(app.view(v.id()));
        return Optional.empty();
    }

    static View waitFor(App app, String title) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            Optional<View> v = window(app, title);
            if (v.isPresent()) return v.get();
            Thread.sleep(100);
        }
        throw new AssertionError("no window titled " + title + ": " + app.views().join());
    }

    static String text(View view) {
        return String.valueOf(view.eval("document.body ? document.body.innerText : ''").join());
    }

    static void until(View view, String part) throws InterruptedException {
        for (int i = 0; i < 100 && !text(view).contains(part); i++) Thread.sleep(100);
        assertTrue(text(view).contains(part), "never showed '" + part + "': " + text(view));
    }

    @Test
    @Timeout(60)
    void whatsNewOpensOnceAfterAnUpdateAndNotOnTheFirstRun() throws Exception {
        StateStorage disk = StateStorage.memory();
        AutoUpdate.AppRelease v2 = new AutoUpdate.AppRelease("Notes", Version.of("0.2.0"), null, null);
        AutoUpdate.AppRelease v3 = new AutoUpdate.AppRelease("Notes", Version.of("0.3.0"), null, null);
        try (App app = App.start()) {
            UiWindow.session(app).storage(disk);
            AutoUpdate.start(app, v2, Duration.ofHours(6));
            Thread.sleep(800);
            assertTrue(window(app, "What's new in Notes").isEmpty(), "the very first run: nothing to say");
        }
        try (App app = App.start()) {
            UiWindow.session(app).storage(disk);
            AutoUpdate.start(app, v3, Duration.ofHours(6));
            View view = waitFor(app, "What's new in Notes");
            until(view, "What's new in Notes 0.3.0");
            String shown = text(view);
            assertTrue(shown.contains("Auto-update: the app finds") && !shown.contains("Layout panes") && !shown.contains("Not out yet"),
                    "0.3.0's changes only: " + shown);
        }
        try (App app = App.start()) {
            UiWindow.session(app).storage(disk);
            AutoUpdate.start(app, v3, Duration.ofHours(6));
            Thread.sleep(800);
            assertTrue(window(app, "What's new in Notes").isEmpty(), "once");
        }
    }

    @Test
    @Timeout(90)
    void thePromptDownloadsChecksAndRunsTheInstaller() throws Exception {
        String[] keys = UpdateSignature.newKeys();
        release("0.3.0", "the 0.3.0 installer".getBytes(), keys[1]);
        List<Path> ran = new CopyOnWriteArrayList<>();
        try (App app = App.start()) {
            UiWindow.session(app).storage(StateStorage.memory());
            AutoUpdate.AppRelease release = new AutoUpdate.AppRelease("Notes", Version.of("0.2.0"), url("/manifest.json"), keys[0]);
            AutoUpdate auto = AutoUpdate.start(app, release, Duration.ofHours(6))
                    .updater(Updater.of(url("/manifest.json"), keys[0], Version.of("0.2.0")).directory(downloads).installer(ran::add));
            auto.checkNow().join();
            View prompt = waitFor(app, "Update Notes");
            until(prompt, "Notes 0.3.0 is available");
            until(prompt, "Better");   // the release's notes
            prompt.eval("[...document.querySelectorAll('button')].find(b => b.innerText === 'Update now').click()").join();
            until(prompt, "ready to install");
            prompt.eval("[...document.querySelectorAll('button')].find(b => b.innerText === 'Install and restart').click()").join();
            app.whenStopped().get(10, java.util.concurrent.TimeUnit.SECONDS);   // it quits, so the installer can replace it
        }
        assertEquals(1, ran.size());
        assertEquals("Notes-0.3.0.msi", ran.getFirst().getFileName().toString());
    }

}
