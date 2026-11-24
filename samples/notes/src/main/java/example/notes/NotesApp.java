package example.notes;

import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.CloseRequested;
import io.joshuasalcedo.webview4j.Closed;
import io.joshuasalcedo.webview4j.View;
import io.joshuasalcedo.webview4j.WindowOptions;

/**
 * A webview-4j app: Java behind a React page made with @webview4j/ui.
 *
 * <ul>
 *   <li>{@code ./gradlew run} builds the page (web/, with Vite) into the jar's {@code web/} and opens it.</li>
 *   <li>{@code ./gradlew run -Pdev} opens the Vite dev server instead ({@code bun run dev} in web/ first):
 *       save a file and the page changes at once.</li>
 * </ul>
 *
 * The window has no system frame: the page's own TitleBar moves, minimizes, maximizes and closes it.
 */
public final class NotesApp {

    private static final String DEV_SERVER = "http://localhost:5173";

    public static void main(String[] args) {
        boolean dev = Boolean.getBoolean("notes.dev");
        // App.run: the event loop on main's thread, which macOS requires (with -XstartOnFirstThread).
        System.exit(App.run(app -> start(app, dev)));
    }

    private static void start(App app, boolean dev) {
        app.register(new NotesController());
        app.register(new AppearanceController());
        app.register(new ShowcaseController());

        WindowOptions page = dev ? WindowOptions.url(DEV_SERVER).devtools(true) : WindowOptions.app("web");
        View view = app.open(page.title("Notes").size(1100, 720).minSize(640, 420)
                .decorations(false).confirmClose(true)).join();

        // The close button (the page's TitleBar, Alt+F4, the dock) asks first; closing the window quits the app.
        view.subscribe(CloseRequested.class, request -> {
            if (app.confirm("Quit?", "Close Notes?").join()) view.close();
        });
        view.subscribe(Closed.class, closed -> app.quit());

        // ./gradlew run -Pshowcase: the showcase too, from the start.
        if (Boolean.getBoolean("notes.showcase")) new ShowcaseController().open(view);
        // ./gradlew run -Pgallery: the component gallery, written only in Java.
        if (Boolean.getBoolean("notes.gallery")) GalleryScreen.open(app);
    }
}
