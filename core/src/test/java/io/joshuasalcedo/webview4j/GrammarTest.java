package io.joshuasalcedo.webview4j;

import static io.joshuasalcedo.webview4j.ui.Ui.*;
import static org.junit.jupiter.api.Assertions.*;

import io.joshuasalcedo.webview4j.automation.Automation;
import io.joshuasalcedo.webview4j.ui.Component;
import io.joshuasalcedo.webview4j.ui.Screen;
import io.joshuasalcedo.webview4j.ui.Ui;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** Spelling and grammar: in Java through the native library, and in the page as WebAssembly. */
class GrammarTest {

    @Test
    void javaChecksThroughTheNativeLibrary() {
        Grammar english = Grammar.english();
        List<Grammar.Mistake> found = english.check("This is an test.");
        assertEquals(1, found.size(), found.toString());
        Grammar.Mistake an = found.getFirst();
        assertEquals("an", an.found("This is an test."));
        assertEquals(List.of("a"), an.suggestions());
        assertEquals("This is a test.", Grammar.apply("This is an test.", an, "a"));
        assertTrue(english.check("The quick brown fox jumps over the lazy dog.").isEmpty());
        assertTrue(english.check("").isEmpty());
        assertSame(english, Grammar.of("en-US"), "one checker per dialect");
    }

    @Test
    void positionsAsJavaCountsThem() {
        String text = "😀 This is an test.";   // the emoji is two chars in Java
        Grammar.Mistake an = Grammar.english().check(text).stream().filter(m -> m.suggestions().contains("a")).findFirst().orElseThrow();
        assertEquals("an", an.found(text));
        assertEquals(11, an.from());
    }

    @Test
    void dialectsAndFixingEverything() {
        assertTrue(Grammar.of("en-GB").check("The colour of the centre.").isEmpty());
        assertFalse(Grammar.of("en-US").check("The colour of the centre.").isEmpty());
        assertEquals("I received their package.", Grammar.english().fix("I recieved there package."));
        assertTrue(Grammar.english().check("I recieved it.").getFirst().spelling());
    }

    static final class Draft implements Screen {
        String text = "This is an test.";
        int mistakes = -1;

        @Override
        public Component<?> render() {
            return column(grammarTextArea("Message").value(text).onChange(v -> text = v).onMistakes(n -> mistakes = n)).padding(16);
        }
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    @Timeout(90)
    void thePageChecksAsWebAssemblyAndFixesAtTheCaret() throws Exception {
        try (App app = App.start()) {
            Draft draft = new Draft();
            View view = Ui.open(app, draft, o -> o.headless(true).size(600, 400)).join().view();
            Automation ui = Automation.of(view).timeout(Duration.ofSeconds(30));
            ui.waitForText("1 to look at");        // the WebAssembly loaded, checked, and found "an"
            assertEquals(1, draft.mistakes);
            assertEquals("an", String.valueOf(view.eval("document.querySelector('.nt-grammar-backdrop mark').textContent").join()));

            // the caret in "an": what it is and its fix; the fix goes in
            view.eval("(() => { const t = document.querySelector('.nt-grammar-input'); t.focus(); t.setSelectionRange(9, 9);"
                    + " t.dispatchEvent(new Event('select', {bubbles: true})); })()").join();
            ui.button("a").click();
            ui.waitForText("No mistakes");
            for (int i = 0; i < 50 && !draft.text.equals("This is a test."); i++) Thread.sleep(100);
            assertEquals("This is a test.", draft.text);
            assertEquals(0, draft.mistakes);
        }
    }

    static final class Typing implements Screen {
        volatile String text = "";
        volatile int checks;
        volatile int mistakes = -1;

        @Override
        public Component<?> render() {
            return column(grammarTextArea("Message").value(text).checkDelay(Duration.ofMillis(600))
                    .onChange(v -> text = v).onMistakes(n -> { checks++; mistakes = n; })).padding(16);
        }
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    @Timeout(90)
    void itChecksOnlyOnceTypingPauses() throws Exception {
        try (App app = App.start()) {
            Typing screen = new Typing();
            View view = Ui.open(app, screen, o -> o.headless(true).size(600, 400)).join().view();
            Automation.of(view).timeout(Duration.ofSeconds(30)).waitForText("Message");
            Thread.sleep(2_000);   // the WebAssembly loaded, the empty text checked
            int before = screen.checks;

            // a key every 100 ms: each sooner than the 600 ms pause, so no check while typing
            String sentence = "This is an test.";
            String type = """
                    (() => { const t = document.querySelector('.nt-grammar-input');
                      const set = Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value').set;
                      set.call(t, %s); t.dispatchEvent(new Event('input', {bubbles: true})); })()""";
            for (int i = 1; i <= sentence.length(); i++) {
                view.eval(type.formatted(Json.write(sentence.substring(0, i)))).join();
                Thread.sleep(100);
            }
            assertEquals(sentence, screen.text, "Java got every key");
            assertEquals(before, screen.checks, "no check while typing");

            // then the pause: one check, which finds "an"
            for (int i = 0; i < 50 && screen.checks == before; i++) Thread.sleep(100);
            assertEquals(1, screen.mistakes);
            Thread.sleep(1_000);
            assertEquals(before + 1, screen.checks, "checked once, after typing stopped");
        }
    }
}
