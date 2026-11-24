package example.notes.gallery;

import static org.junit.jupiter.api.Assertions.*;

import example.notes.GalleryScreen;
import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.View;
import io.joshuasalcedo.webview4j.ui.Ui;
import io.joshuasalcedo.webview4j.ui.UiWindow;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * The gallery shows every component: each component class is covered by a page, and each page draws in a real
 * window without a component the page cannot show. Only one category: {@code -Dgallery.section=Layout}.
 */
class GalleryTest {

    @Test
    void everyComponentHasADemo() {
        List<Class<?>> missing = new Gallery().missing();
        assertTrue(missing.isEmpty(), "no gallery page covers: " + missing.stream().map(Class::getName).toList());
    }

    @Test
    void aPageCoversOnlyComponents() {
        List<Class<?>> all = Gallery.components();
        assertTrue(all.size() > 50, "the component classes were found: " + all.size());
        for (Gallery.Page p : new Gallery().pages()) {
            for (Class<?> c : p.covers()) assertTrue(all.contains(c), p.name() + " covers " + c.getName() + ", not a component");
        }
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    @Timeout(600)
    void everyPageDraws() throws Exception {
        String only = System.getProperty("gallery.section", "");
        try (App app = App.start()) {
            GalleryScreen screen = new GalleryScreen();
            UiWindow window = Ui.open(app, screen, o -> o.headless(true).size(1180, 900)).join();
            View view = window.view();
            List<String> broken = new ArrayList<>();
            for (Gallery.Page page : screen.gallery().pages()) {
                if (!only.isEmpty() && !page.section().equals(only)) continue;
                screen.show(page.name());
                window.refresh().join();
                String text = "";
                for (int i = 0; i < 50; i++) {
                    text = String.valueOf(view.eval("document.body.innerText").join());
                    if (text.contains("The Java") && text.contains(page.name())) break;
                    Thread.sleep(100);
                }
                if (!text.contains("The Java")) broken.add(page.name() + ": the page did not draw");
                else if (text.contains("No component \"")) broken.add(page.name() + ": " + text.substring(text.indexOf("No component \"")).lines().findFirst().orElse(""));
            }
            assertTrue(broken.isEmpty(), "pages that do not draw: " + broken);
        }
    }
}
