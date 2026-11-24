package example.notes;

import io.joshuasalcedo.webview4j.ui.WebHost;

/**
 * The component gallery on the web: the same Java screen, in a browser, each tab a screen of its own.
 * {@code ./gradlew runWeb} (or {@code just gallery-web}), then open http://localhost:8080/.
 */
public final class GalleryOnTheWeb {

    public static void main(String[] args) throws InterruptedException {
        int port = Integer.getInteger("notes.port", 8080);
        try (WebHost host = WebHost.of(GalleryScreen::new).title("Component gallery").port(port).start()) {
            System.out.println("The gallery is on the web at " + host.url() + " (Ctrl+C stops it)");
            Thread.currentThread().join();
        }
    }
}
