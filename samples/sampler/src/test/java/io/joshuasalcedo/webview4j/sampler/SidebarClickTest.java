package io.joshuasalcedo.webview4j.sampler;

import static org.junit.jupiter.api.Assertions.*;

import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.View;
import io.joshuasalcedo.webview4j.automation.Automation;
import io.joshuasalcedo.webview4j.ui.Ui;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** A page clicked in the sidebar is the page shown: never its neighbour. */
@EnabledOnOs(OS.WINDOWS)
class SidebarClickTest {

    @Test
    @Timeout(120)
    void eachPageClickedIsThePageShown() throws Exception {
        try (App app = App.start()) {
            SamplerScreen screen = new SamplerScreen();
            View view = Ui.open(app, screen, o -> o.headless(true).size(1180, 900)).join().view();
            Automation ui = Automation.of(view).timeout(Duration.ofSeconds(10));
            ui.waitForText("webview-4j Sampler");
            ui.button("Documents").click();
            Field selected = SamplerScreen.class.getDeclaredField("selected");
            selected.setAccessible(true);
            for (String page : List.of("CSV viewer", "PDF viewer", "Word viewer", "Excel viewer", "Document splits")) {
                ui.button(page).click();
                for (int i = 0; i < 30 && !page.equals(selected.get(screen)); i++) Thread.sleep(100);
                assertEquals(page, selected.get(screen), "clicked " + page);
            }
            // a page that loads a library (PDFium) stays the page shown while it loads, and draws
            ui.button("PDF viewer").click();
            Thread.sleep(10_000);
            assertEquals("PDF viewer", selected.get(screen), "still the PDF viewer after it loaded");
            // it drew: PDFium's pages are in the page, and it counted them
            ui.waitForText("of 4");
            Object drawn = view.eval("document.querySelectorAll('.nt-pdf img, .nt-pdf canvas').length").join();
            assertTrue(((Number) drawn).intValue() > 0, "pages drawn: " + drawn);
            // the downloaded IRS form: six pages
            ui.role("radio", "IRS Form W-9").click();
            ui.waitForText("of 6");
        }
    }
}
