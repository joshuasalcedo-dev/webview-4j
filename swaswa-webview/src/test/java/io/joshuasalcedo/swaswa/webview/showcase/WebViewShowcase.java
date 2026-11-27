package io.joshuasalcedo.swaswa.webview.showcase;

import io.joshuasalcedo.swaswa.ffi.App;
import io.joshuasalcedo.swaswa.ffi.WindowOptions;
import io.joshuasalcedo.swaswa.webview.WebViewApplication;

/**
 * swaswa-webview in one window: an app that brings its own web page (here plain HTML, {@code web-showcase/index.html}
 * on the class path), which calls Java the way a page calls a server: {@link NotesController} answers its requests.
 * Not part of the jar (a test source). {@code just webview-showcase}.
 */
public final class WebViewShowcase extends WebViewApplication {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(App app) {
        open(WindowOptions.app("web-showcase").title("swaswa-webview: notes").size(720, 640), new NotesController()).join();
    }
}
