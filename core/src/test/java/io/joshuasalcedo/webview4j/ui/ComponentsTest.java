package io.joshuasalcedo.webview4j.ui;

import static io.joshuasalcedo.webview4j.ui.Ui.*;
import static org.junit.jupiter.api.Assertions.*;

import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.View;
import io.joshuasalcedo.webview4j.ui.components.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** Every component of the kit, from Java, in a real window: each renders, and its events reach Java typed. */
@EnabledOnOs(OS.WINDOWS)
class ComponentsTest {

    static final class Everything implements Screen {
        Double number;
        List<String> tokens = List.of("vip");
        Long amount = 49900L;
        String tab = "one";
        boolean dialogOpen = true;
        String dialogAnswer = "";
        int loads;
        List<String> sent = new ArrayList<>();

        @Override
        public Component<?> render() {
            Map<String, String> none = new LinkedHashMap<>();
            return column(
                    breadcrumb().crumb("Home", () -> {}).crumb("Orders"),
                    tabs("Views").tab("one", "One").tab("two", "Two", true).value(tab).onChange(v -> tab = v),
                    text("tab " + tab),
                    searchField("Search").placeholder("Find"),
                    passwordField("Password"),
                    numberField("Count").value(number).after("pcs").onChange(v -> number = v),
                    text("number " + number),
                    tokenField("Tags").value(tokens).onChange(v -> tokens = v),
                    moneyField("Price", "PHP").value(amount).onChange(v -> amount = v),
                    quantityField("Weight", Unit.KILOGRAM).value(2.5).units(List.of(Unit.KILOGRAM, Unit.POUND)),
                    quantity(2.5, Unit.KILOGRAM),
                    label("A label"), inlineCode("x = 1"), shortcut("Mod+K"), statusDot(DotState.ONLINE),
                    pillGroup(pill("A"), pill("B"), pill("C"), pill("D")).max(2),
                    logView("Log", List.of(new LogView.LogLine(Instant.now(), LogLevel.INFO, "started"))),
                    messageBubble("Hello there", false).sender("Ana"), messageBubble("Hi!", true), typing("Ana"),
                    composer("Message").onSend(sent::add),
                    fileIcon("report.pdf"), fileChip("report.pdf").size(1200),
                    fileTree("Files", List.of(FileNode.folder("src", FileNode.file("App.java")), FileNode.file("README.md"))),
                    fileList("List", List.of(FileNode.file("a.txt", 10, Instant.now()))),
                    swipeable(text("swipe me")).left("Delete", "error", () -> {}),
                    infiniteScroll(text("row 1")).hasMore(loads < 1).onLoadMore(() -> loads++),
                    inspector("Details", inspectorSection("General", text("inside the inspector"))),
                    mdi("Docs", mdiWindow("w1", "First window", text("in the first window"))),
                    richText("Notes").value("<p>rich</p>"),
                    toolbar("Bar", toolbarButton("A"), toolbarSeparator(), toolbarButton("B")),
                    dialog("Delete it?").message("It goes for good.").open(dialogOpen)
                            .cancel("Cancel", () -> { dialogOpen = false; dialogAnswer = "cancelled"; })
                            .destructive("Delete", () -> { dialogOpen = false; dialogAnswer = "deleted"; }),
                    text("dialog " + dialogAnswer)).padding(16).gap(8).scroll();
        }
    }

    @Test
    @Timeout(120)
    void everyComponentRendersAndItsEventsReachJava() throws Exception {
        try (App app = App.start()) {
            app.subscribe(io.joshuasalcedo.webview4j.LogRecord.class, log -> {
                if (log.level() == io.joshuasalcedo.webview4j.LogRecord.Level.ERROR) System.out.println("PAGE ERROR " + log.message());
            });
            Everything screen = new Everything();
            View view = Ui.open(app, screen, o -> o.headless(true).size(1000, 900)).join().view();
            String text = until(view, "inside the inspector");
            assertFalse(text.contains("No component"), "every component is known to the renderer: " + text);
            for (String expected : List.of("Home", "Orders", "One", "Two", "A label", "started", "Hello there", "in the first window",
                    "report", ".pdf", "1.2 KB", "README.md", "Ana is typing", "swipe me", "Delete it?", "It goes for good.")) {
                assertTrue(text.contains(expected), "shows '" + expected + "'");
            }

            // events arrive typed: a tab, a dialog's button
            view.eval("[...document.querySelectorAll('[role=tab]')].find(t => t.textContent.includes('Two')).click()").join();
            until(view, "tab two");
            assertEquals("two", screen.tab);
            view.eval("[...document.querySelectorAll('button')].find(b => b.textContent === 'Delete').click()").join();
            until(view, "dialog deleted");
            assertFalse(screen.dialogOpen);
        }
    }

    static String until(View view, String part) throws InterruptedException {
        String text = "";
        for (int i = 0; i < 150; i++) {
            text = String.valueOf(view.eval("document.body.innerText").join());
            if (text.contains(part)) return text;
            Thread.sleep(100);
        }
        throw new AssertionError("never showed '" + part + "': " + text);
    }
}
