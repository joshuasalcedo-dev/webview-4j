package io.joshuasalcedo.webview4j.ui.components;

import static io.joshuasalcedo.webview4j.ui.Ui.*;
import static org.junit.jupiter.api.Assertions.*;

import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.View;
import io.joshuasalcedo.webview4j.ui.Component;
import io.joshuasalcedo.webview4j.ui.Screen;
import io.joshuasalcedo.webview4j.ui.Ui;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** A rename asks for the new name and Java gets both; a paragraph keeps code inside its text. */
@EnabledOnOs(OS.WINDOWS)
class GapsWindowTest {

    static final class Files implements Screen {
        String renamed = "";

        @Override
        public Component<?> render() {
            return column(
                    paragraph("Run ", inlineCode("./gradlew run"), " to start."),
                    fileList("Files", List.of(FileNode.file("notes.txt", 1200, null))).onRename((path, name) -> renamed = path + " -> " + name),
                    text("renamed " + renamed));
        }
    }

    @Test
    @Timeout(60)
    void aRenameAsksForTheNameAndJavaGetsBoth() throws Exception {
        try (App app = App.start()) {
            Files screen = new Files();
            View view = Ui.open(app, screen, o -> o.headless(true).size(700, 500)).join().view();
            until(view, "notes.txt");
            // one element holds the whole sentence, the code inside it (whitespace as the page lays it out aside)
            assertTrue(io.joshuasalcedo.webview4j.automation.Automation.of(view).text("Run ./gradlew run to start.").exists(),
                    "one paragraph, the code inside it");

            // the file's menu: Rename
            view.eval("""
                    (() => { const row = [...document.querySelectorAll('[role=row], [role=option], li, tr')].find(e => e.innerText.includes('notes.txt'));
                      row.dispatchEvent(new MouseEvent('contextmenu', {bubbles: true, clientX: 50, clientY: 50})); })()
                    """).join();
            Thread.sleep(300);
            view.eval("[...document.querySelectorAll('[role=menuitem]')].find(m => m.innerText.includes('Rename')).click()").join();
            until(view, "A new name for notes.txt");
            view.eval("""
                    (() => { const i = document.querySelector('input[aria-label="New name"]');
                      const set = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
                      set.call(i, 'ideas.txt'); i.dispatchEvent(new Event('input', {bubbles: true}));
                      i.dispatchEvent(new KeyboardEvent('keydown', {key: 'Enter', bubbles: true})); })()
                    """).join();
            until(view, "renamed notes.txt -> ideas.txt");
            assertEquals("notes.txt -> ideas.txt", screen.renamed);
        }
    }

    static String js(View view, String script) {
        return String.valueOf(view.eval(script).join());
    }

    static void until(View view, String part) throws InterruptedException {
        String text = "";
        for (int i = 0; i < 100; i++) {
            text = js(view, "document.body.innerText");
            if (text.contains(part)) return;
            Thread.sleep(100);
        }
        throw new AssertionError("never showed '" + part + "': " + text);
    }
}
