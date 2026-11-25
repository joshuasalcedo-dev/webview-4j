package io.joshuasalcedo.webview4j.automation;

import static io.joshuasalcedo.webview4j.ui.Ui.*;
import static org.junit.jupiter.api.Assertions.*;

import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.View;
import io.joshuasalcedo.webview4j.ui.Component;
import io.joshuasalcedo.webview4j.ui.Screen;
import io.joshuasalcedo.webview4j.ui.Ui;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** Driving a real window as a person would, recording what is done, saving it, and replaying it. */
@EnabledOnOs(OS.WINDOWS)
class AutomationWindowTest {

    @TempDir
    Path dir;

    /** A form, a list, a menu: what the steps act on, and what they did, said in a line Java keeps. */
    static final class Settings implements Screen {
        String name = "";
        String password = "";
        boolean remember;
        String country = "ph";
        String saved = "nothing saved";
        String opened = "";

        @Override
        public Component<?> render() {
            return column(
                    textField("Name").value(name).onChange(v -> name = v),
                    textField("Password").password().value(password).onChange(v -> password = v),
                    checkbox("Remember me").checked(remember).onChange(v -> remember = v),
                    select("Country", Map.of("ph", "Philippines", "jp", "Japan")).value(country).onChange(v -> country = v),
                    button("Save").accent().onClick(() -> saved = "saved " + name + " " + country + (remember ? " remembered" : "")
                            + (password.isEmpty() ? "" : " with a password")),
                    list("People", List.of(listItem("Ana Reyes").key("ana").onDoubleClick(() -> opened = "opened Ana").menu("Message", () -> opened = "message Ana"))),
                    text(saved + " " + opened)).padding(16).gap(12);
        }
    }

    @Test
    @Timeout(120)
    void drivingRecordingSavingAndReplaying() throws Exception {
        try (App app = App.start()) {
            Settings first = new Settings();
            View view = Ui.open(app, first, o -> o.headless(true).size(700, 700)).join().view();
            Automation ui = Automation.of(view);
            ui.waitForText("nothing saved");

            // a target that is not there: the error says what is
            AutomationException missing = assertThrows(AutomationException.class, () -> ui.timeout(Duration.ofMillis(400)).button("Delete everything").click());
            assertTrue(missing.getMessage().contains("no button \"Delete everything\"") && missing.seen().contains("button \"Save\""), missing.getMessage());
            ui.timeout(Duration.ofSeconds(5));

            // record what is done
            Recorder recorder = ui.record();
            ui.field("Name").type("Ana");
            ui.field("Password").type("correct horse");
            ui.field("Name").focus();               // leaving the password: it is written down
            ui.checkbox("Remember me").check();
            ui.field("Country").select("Japan");
            ui.button("Save").click();
            ui.waitForText("saved Ana jp remembered with a password");
            ui.text("Ana Reyes").doubleClick();
            ui.waitForText("opened Ana");
            Script script = recorder.stop("change the settings");

            List<String> kinds = script.steps().stream().map(s -> s.getClass().getSimpleName()).toList();
            assertTrue(kinds.containsAll(List.of("Type", "Click", "Select", "DoubleClick")), kinds.toString());
            String json = script.toJson();
            assertTrue(json.contains("${PASSWORD}") && !json.contains("correct horse"), "a password is never written down:\n" + json);
            assertTrue(json.contains("\"button\"") || json.contains("\"role\":\"button\""), json);

            // saved, read back, replayed on a fresh screen: the same end
            Path file = dir.resolve("settings.automation.json");
            script.save(file);
            Script again = Script.load(file);
            assertEquals(script, again);

            Settings fresh = new Settings();
            View second = Ui.open(app, fresh, o -> o.headless(true).size(700, 700)).join().view();
            Automation.of(second).waitForText("nothing saved");
            Script.Report report = again.with(new Script.Step.WaitForText("opened Ana")).run(Automation.of(second), Map.of("PASSWORD", "correct horse"), dir);
            assertTrue(report.ok(), report.failure() + "\n" + json);
            assertEquals("saved Ana jp remembered with a password", fresh.saved);
            assertEquals("opened Ana", fresh.opened);

            // without the password: the step says what is missing
            Settings third = new Settings();
            View view3 = Ui.open(app, third, o -> o.headless(true)).join().view();
            Script.Report noPassword = again.run(Automation.of(view3).timeout(Duration.ofSeconds(2)), Map.of(), dir);
            if (System.getenv("PASSWORD") == null) assertTrue(noPassword.failure().contains("${PASSWORD}"), noPassword.failure());

            // as Java
            String java = script.toJava("ChangeTheSettingsTest");
            assertTrue(java.contains("ui.field(\"Name\")") && java.contains(".type(System.getenv(\"PASSWORD\"))") && java.contains("void changeTheSettings()"), java);
        }
    }

    @Test
    @Timeout(60)
    void aScriptWrittenByHandWithItsShortForms() throws Exception {
        Script hand = Script.parse("""
                {"name": "message Ana", "steps": [
                  {"rightClick": {"text": "Ana Reyes"}},
                  {"click": {"menuitem": "Message"}},
                  {"waitFor": {"text": "message Ana"}},
                  {"expect": {"text": "message Ana"}, "text": "message"},
                  {"screenshot": "shots/after.png"},
                  {"pause": 50}
                ]}""");
        assertEquals(6, hand.steps().size());
        try (App app = App.start()) {
            Settings screen = new Settings();
            View view = Ui.open(app, screen, o -> o.headless(true).size(700, 700)).join().view();
            Automation.of(view).waitForText("nothing saved");
            Script.Report report = hand.run(Automation.of(view), Map.of(), dir);
            assertTrue(report.ok(), String.valueOf(report.failure()));
            assertEquals("message Ana", screen.opened);
            assertTrue(Files.size(dir.resolve("shots/after.png")) > 1000, "the screenshot step");
        }
    }
}
