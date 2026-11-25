package io.joshuasalcedo.webview4j.sampler;

import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.Closed;
import io.joshuasalcedo.webview4j.automation.Automation;
import io.joshuasalcedo.webview4j.automation.Recorder;
import io.joshuasalcedo.webview4j.automation.Script;
import java.nio.file.Path;
import java.util.Map;

/**
 * The webview-4j Sampler: every component, working, with the Java for each, in one desktop app written only in Java
 * (as AtlantaFX's Sampler is for its controls). {@code ./gradlew run}, {@code ./gradlew nativeCompile} for a native
 * executable, {@code ./gradlew packageApp} for its installer. Closing its window quits it.
 * <pre>
 * Sampler --record docs/tour.automation.json     what is done until the window closes, saved as a script
 * Sampler --automate docs/tour.automation.json   the script replayed (its screenshots and recordings under docs/),
 *                                                 the report printed; the exit code is 1 if a step failed
 * </pre>
 */
public final class Sampler {

    public static void main(String[] args) {
        String mode = args.length >= 2 ? args[0] : "";
        Path file = args.length >= 2 ? Path.of(args[1]) : null;
        int[] exit = {0};
        int code = App.run(app -> {
            // What's new after an update, and updates when its release has a manifest and key (the build's release tasks).
            if (mode.isEmpty()) io.joshuasalcedo.webview4j.ui.components.usecase.autoupdate.AutoUpdate.start(app);
            SamplerScreen.open(app).thenAccept(window -> {
                switch (mode) {
                    case "--record" -> {
                        Recorder recorder = Automation.of(window.view()).record();
                        System.out.println("Recording: use the Sampler, then close its window to save " + file);
                        window.view().subscribe(Closed.class, closed -> {
                            Script script = recorder.stop(file.getFileName().toString().replaceFirst("\\.automation\\.json$|\\.json$", ""));
                            script.save(file);
                            System.out.println("Saved " + script.steps().size() + " steps in " + file);
                            app.quit();
                        });
                    }
                    case "--automate" -> Thread.ofVirtual().start(() -> {
                        Script script = Script.load(file);
                        Automation ui = Automation.of(window.view());
                        ui.waitForText("webview-4j Sampler");
                        Script.Report report = script.run(ui, Map.of(), file.toAbsolutePath().getParent());
                        for (Script.Report.Done d : report.steps()) {
                            System.out.printf("%s %5d ms  %s%n", d.error() == null ? "ok  " : "FAIL", d.millis(), d.step());
                        }
                        System.out.println(report.ok() ? "All " + report.steps().size() + " steps done." : "Failed at " + report.failure());
                        exit[0] = report.ok() ? 0 : 1;
                        app.quit();
                    });
                    default -> window.view().subscribe(Closed.class, closed -> app.quit());
                }
            });
        });
        System.exit(exit[0] != 0 ? exit[0] : code);
    }
}
