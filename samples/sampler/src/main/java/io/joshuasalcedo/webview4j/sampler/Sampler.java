package io.joshuasalcedo.webview4j.sampler;

import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.Closed;

/**
 * The webview-4j Sampler: every component, working, with the Java for each, in one desktop app written only in Java
 * (as AtlantaFX's Sampler is for its controls). {@code ./gradlew run}, {@code ./gradlew nativeCompile} for a native
 * executable, {@code ./gradlew packageApp} for its installer. Closing its window quits it.
 */
public final class Sampler {

    public static void main(String[] args) {
        App.run(app -> {
            // What's new after an update, and updates when its release has a manifest and key (the build's release tasks).
            io.joshuasalcedo.webview4j.ui.components.usecase.autoupdate.AutoUpdate.start(app);
            SamplerScreen.open(app).thenAccept(window -> window.view().subscribe(Closed.class, closed -> app.quit()));
        });
    }
}
