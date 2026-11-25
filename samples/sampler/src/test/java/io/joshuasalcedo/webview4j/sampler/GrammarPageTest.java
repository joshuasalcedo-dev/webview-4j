package io.joshuasalcedo.webview4j.sampler;

import static org.junit.jupiter.api.Assertions.*;

import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.View;
import io.joshuasalcedo.webview4j.automation.Automation;
import io.joshuasalcedo.webview4j.ui.Ui;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** The Grammar page: the page checks as WebAssembly, and its buttons check and fix in Java. */
@EnabledOnOs(OS.WINDOWS)
class GrammarPageTest {

    @Test
    @Timeout(120)
    void thePageAndJavaBothCheck() throws Exception {
        try (App app = App.start()) {
            SamplerScreen screen = new SamplerScreen();
            screen.show("Grammar");
            View view = Ui.open(app, screen, o -> o.headless(true).size(1180, 900)).join().view();
            Automation ui = Automation.of(view).timeout(Duration.ofSeconds(30));
            ui.waitForText("4 in the page");
            ui.button("Check in Java").click();
            ui.waitForText("Java found: recieved (Spelling)");
            ui.button("Fix all in Java").click();
            ui.waitForText("No mistakes");
            ui.waitForText("0 in the page");
            assertTrue(ui.pageText().contains("I received their package yesterday"), ui.pageText());
        }
    }
}
