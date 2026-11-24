package io.joshuasalcedo.webview4j.ui;

import static io.joshuasalcedo.webview4j.ui.Ui.*;
import static org.junit.jupiter.api.Assertions.*;

import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.View;
import io.joshuasalcedo.webview4j.WindowOptions;
import io.joshuasalcedo.webview4j.ui.state.Property;
import io.joshuasalcedo.webview4j.ui.state.Store;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * The web host in a real browser engine (a hidden window opening the host's address, as a browser would): the page,
 * its WebSocket bridge, the renderer, and state kept in the browser's IndexedDB across a restart of the server.
 */
@EnabledOnOs(OS.WINDOWS)
class WebHostBrowserTest {

    static final Store<String> NOTE = Store.of("note", String.class, () -> "none").property(Property.of().persist());

    static final class Notes implements Screen {
        @Override
        public Component<?> render() {
            return column(text("note " + NOTE.get()), button("Keep").onClick(() -> NOTE.set("kept on the web")));
        }
    }

    @Test
    @Timeout(90)
    void theScreenRunsInABrowserAndItsStateStaysInIt() throws Exception {
        int port;
        try (App app = App.start()) {
            try (WebHost host = WebHost.of(Notes::new).title("Notes on the web").start()) {
                port = host.port();
                View view = app.open(WindowOptions.url(host.url().toString()).headless(true).size(700, 500)).join();
                until(view, "note none");
                assertEquals("Notes on the web", String.valueOf(view.eval("document.title").join()));
                view.eval("[...document.querySelectorAll('button')].find(b => b.innerText === 'Keep').click()").join();
                until(view, "note kept on the web");
                Thread.sleep(500);   // written to IndexedDB in the background
                assertEquals(1, host.tabs());
            }
            // the server restarts on the same address: its sessions are gone, the browser still has the note
            try (WebHost again = WebHost.of(Notes::new).port(port).start()) {
                View view = app.open(WindowOptions.url(again.url().toString()).headless(true).size(700, 500)).join();
                until(view, "note kept on the web");
            }
        }
    }

    static final class Asker implements Screen {
        String said = "nothing yet";

        @Override
        public Component<?> render() {
            return column(text("said " + said),
                    button("Delete").onClick(() -> Host.current().confirm("Delete the note?", "It cannot be undone.")
                            .thenAccept(yes -> said = yes ? "deleted" : "kept")));
        }
    }

    @Test
    @Timeout(60)
    void aQuestionIsADialogInThePage() throws Exception {
        try (App app = App.start(); WebHost host = WebHost.of(Asker::new).start()) {
            View view = app.open(WindowOptions.url(host.url().toString()).headless(true).size(700, 500)).join();
            until(view, "said nothing yet");
            view.eval("[...document.querySelectorAll('button')].find(b => b.innerText === 'Delete').click()").join();
            until(view, "Delete the note?");
            assertEquals("dialog", String.valueOf(view.eval("document.querySelector('.w4j-dialog').getAttribute('role')").join()));
            view.eval("[...document.querySelectorAll('.w4j-dialog button')].find(b => b.innerText === 'Cancel').click()").join();
            until(view, "said kept");
        }
    }

    static void until(View view, String part) throws InterruptedException {
        String text = "";
        for (int i = 0; i < 150; i++) {
            text = String.valueOf(view.eval("document.body ? document.body.innerText : ''").join());
            if (text.contains(part)) return;
            Thread.sleep(100);
        }
        throw new AssertionError("never showed '" + part + "': " + text);
    }
}
