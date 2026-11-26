package io.joshuasalcedo.webview4j.explorer;

import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.WebviewApplication;

/**
 * The webview-4j API explorer: every public type and member of core, its javadoc, its pretty-docs page, and a live
 * playground to call it and see what it gives. {@code ./gradlew :explorer:run}.
 * <p>
 * What it lists is made by the build from the sources (pretty-docs' model, see {@link ApiIndex}), never written by
 * hand, so it shows the API as it is in this checkout.
 */
public final class Explorer extends WebviewApplication {

    @Override
    public void start(App app) {
        new ExplorerWindow(app).open(app);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
