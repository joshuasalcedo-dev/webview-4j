package io.joshuasalcedo.webview4j.spring;

import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.View;
import java.util.concurrent.CompletableFuture;

/** devtools' API explorer, when webview-4j-devtools is on the class path (it is optional for the starter). */
final class ApiExplorerLauncher {

    private ApiExplorerLauncher() {}

    static boolean available() {
        try {
            Class.forName("io.joshuasalcedo.webview4j.devtools.ApiExplorer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /** Opens it on the description at {@code url}; its view once open. Only call when {@link #available()}. */
    static CompletableFuture<View> open(App app, String url, String title, boolean headless) {
        return io.joshuasalcedo.webview4j.devtools.ApiExplorer.of(url).title(title).headless(headless).open(app)
                .thenApply(window -> window.view());
    }
}
