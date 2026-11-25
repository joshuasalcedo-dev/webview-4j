package io.joshuasalcedo.webview4j.ui;

import static io.joshuasalcedo.webview4j.ui.ComponentsTest.until;
import static io.joshuasalcedo.webview4j.ui.Ui.*;
import static org.junit.jupiter.api.Assertions.*;

import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.Json;
import io.joshuasalcedo.webview4j.View;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** What a field does not take does not appear as it is typed; fields are a form's width, not the window's. */
class FieldInputTest {

    @Test
    void allowTakesOneCharacterClass() {
        textField("Code").allow("[0-9]").allow("[A-Za-z_-]").allow("[^\\s]");
        assertThrows(IllegalArgumentException.class, () -> textField("Code").allow("0-9"));
        assertThrows(IllegalArgumentException.class, () -> textField("Code").allow("[0-9]+"));
        assertThrows(IllegalArgumentException.class, () -> textField("Code").maxLength(-1));
    }

    static final class Form implements Screen {
        volatile String code = "";
        volatile Double count;

        @Override
        public Component<?> render() {
            return column(
                    textField("Code").allow("[0-9]").maxLength(4).value(code).onChange(v -> code = v),
                    numberField("Count").fractionDigits(0).min(0).value(count).onChange(v -> count = v),
                    textField("Wide").grow(),
                    text("code " + code)).padding(16).gap(8);
        }
    }

    /** Types `text` into the field labelled `label` as a person's input event does. */
    static void type(View view, String label, String text) {
        view.eval("""
                (() => { const input = [...document.querySelectorAll('input')].find(i => i.labels?.[0]?.textContent?.includes(%s)
                    || i.closest('label')?.textContent?.includes(%s));
                  const set = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
                  input.focus(); set.call(input, %s); input.dispatchEvent(new Event('input', {bubbles: true})); })()"""
                .formatted(Json.write(label), Json.write(label), Json.write(text))).join();
    }

    static String value(View view, String label) {
        return String.valueOf(view.eval("""
                [...document.querySelectorAll('input')].find(i => i.labels?.[0]?.textContent?.includes(%s)
                    || i.closest('label')?.textContent?.includes(%s))?.value ?? null"""
                .formatted(Json.write(label), Json.write(label))).join());
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    @Timeout(90)
    void whatAFieldDoesNotTakeDoesNotAppear() throws Exception {
        try (App app = App.start()) {
            Form form = new Form();
            View view = Ui.open(app, form, o -> o.headless(true).size(1200, 500)).join().view();
            until(view, "code");

            type(view, "Code", "12a3b45");   // pasted: the letters left out, then cut at 4
            until(view, "code 1234");
            assertEquals("1234", value(view, "Code"));

            type(view, "Count", "7");
            type(view, "Count", "7x");      // a letter: it does not appear
            assertEquals("7", value(view, "Count"));
            type(view, "Count", "7.5");     // a whole number: no decimal mark
            assertEquals("7", value(view, "Count"));
            type(view, "Count", "-7");      // not below zero (min 0): no minus
            assertEquals("7", value(view, "Count"));

            // a form's width, not the window's: 1200 px wide, the fields at most 360 unless they grow
            double code = ((Number) view.eval("[...document.querySelectorAll('input')][0].closest('label, .nt-field, div').getBoundingClientRect().width").join()).doubleValue();
            assertTrue(code <= 362, "the code field is a form field's width: " + code);
            double wide = ((Number) view.eval("[...document.querySelectorAll('input')][2].getBoundingClientRect().width").join()).doubleValue();
            assertTrue(wide > 600, "grow() still takes the room: " + wide);
        }
    }
}
