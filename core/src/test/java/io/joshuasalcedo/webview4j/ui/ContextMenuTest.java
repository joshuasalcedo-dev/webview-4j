package io.joshuasalcedo.webview4j.ui;

import static io.joshuasalcedo.webview4j.ui.ComponentsTest.until;
import static io.joshuasalcedo.webview4j.ui.Ui.*;
import static org.junit.jupiter.api.Assertions.*;

import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.View;
import io.joshuasalcedo.webview4j.ui.components.ContextMenu;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** A right-click opens the menu Java made; choosing an item runs its Java code; a greyed one cannot be chosen. */
@EnabledOnOs(OS.WINDOWS)
class ContextMenuTest {

    static final class Files implements Screen {
        volatile String said = "nothing";
        volatile boolean pinned;

        @Override
        public Component<?> render() {
            return column(
                    contextMenu(text("report.pdf"))
                            .item("Open", () -> said = "opened")
                            .item(ContextMenu.menuItem("Copy", () -> said = "copied").shortcut("Mod+C"))
                            .separator()
                            .check("Pinned", pinned, on -> pinned = on)
                            .item(ContextMenu.menuItem("Delete", () -> said = "deleted").disabled(true)),
                    text("said " + said)).padding(16);
        }
    }

    private static void rightClick(View view, String text) {
        view.eval("""
                (() => { const t = [...document.querySelectorAll('span, p, div')].find(e => e.textContent === %s && !e.children.length);
                  const r = t.getBoundingClientRect();
                  t.dispatchEvent(new MouseEvent('contextmenu', {bubbles: true, cancelable: true, clientX: r.left + 5, clientY: r.top + 5})); })()"""
                .formatted(io.joshuasalcedo.webview4j.Json.write(text))).join();
    }

    private static void choose(View view, String label) throws InterruptedException {
        String find = "[...document.querySelectorAll('[role^=menuitem]')].find(m => m.innerText.includes(%s))"
                .formatted(io.joshuasalcedo.webview4j.Json.write(label));
        for (int i = 0; i < 100 && !Boolean.TRUE.equals(view.eval("!!" + find).join()); i++) Thread.sleep(50);
        view.eval(find + ".click()").join();
    }

    @Test
    @Timeout(90)
    void aRightClickOpensTheMenuAndAnItemRunsJava() throws Exception {
        try (App app = App.start()) {
            Files screen = new Files();
            View view = Ui.open(app, screen, o -> o.headless(true).size(600, 400)).join().view();
            until(view, "said nothing");

            rightClick(view, "report.pdf");
            choose(view, "Open");
            until(view, "said opened");

            rightClick(view, "report.pdf");
            choose(view, "Pinned");
            for (int i = 0; i < 50 && !screen.pinned; i++) Thread.sleep(100);
            assertTrue(screen.pinned, "the check item gives the other state");

            rightClick(view, "report.pdf");
            assertEquals("true", String.valueOf(view.eval(
                    "[...document.querySelectorAll('[role^=menuitem]')].find(m => m.innerText.includes('Delete'))?.getAttribute('aria-disabled')").join()),
                    "a greyed item says so");
            choose(view, "Delete");
            Thread.sleep(500);
            assertEquals("opened", screen.said, "a greyed item runs nothing");
        }
    }
}
