package io.joshuasalcedo.webview4j.ui;

import static io.joshuasalcedo.webview4j.ui.ComponentsTest.until;
import static io.joshuasalcedo.webview4j.ui.Ui.*;
import static org.junit.jupiter.api.Assertions.*;

import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.Json;
import io.joshuasalcedo.webview4j.View;
import io.joshuasalcedo.webview4j.ui.components.ComboboxOption;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** shadcn/ui's overlays, pickers and menus from Java, used as a person would: each answers Java. */
@EnabledOnOs(OS.WINDOWS)
class OverlaysTest {



    static final class Screen implements io.joshuasalcedo.webview4j.ui.Screen {
        volatile String country = "ph";
        volatile String day = "";
        volatile String ran = "";
        volatile boolean palette;
        volatile boolean sheetOpen;

        @Override
        public Component<?> render() {
            return column(
                    combobox("Country", List.of(new ComboboxOption("ph", "Philippines"), new ComboboxOption("jp", "Japan"), new ComboboxOption("de", "Germany")))
                            .value(country).onChange(v -> country = v),
                    calendar(day).month("2026-11").onChange(v -> day = v),
                    popover("Filters", text("inside the popover")),
                    menubar().menu("File", m -> m.item("New", "Mod+N", () -> ran = "menu new")),
                    commandPalette(palette).onOpenChange(open -> palette = open).command("Files", "Export", () -> ran = "command export"),
                    button("Open sheet").onClick(() -> sheetOpen = true),
                    sheet(sheetOpen, text("inside the sheet")).title("Sheet").onOpenChange(open -> sheetOpen = open),
                    text("country " + country + " day " + day + " ran " + ran)).padding(16).gap(8);
        }
    }

    private static Object js(View view, String script) {
        return view.eval(script).join();
    }

    private static void waitFor(View view, String condition) throws InterruptedException {
        for (int i = 0; i < 100 && !Boolean.TRUE.equals(js(view, "!!(" + condition + ")")); i++) Thread.sleep(50);
        assertEquals(true, js(view, "!!(" + condition + ")"), "never true: " + condition);
    }

    private static void click(View view, String selector, String text) throws InterruptedException {
        String find = "[...document.querySelectorAll(%s)].find(e => e.textContent.trim() === %s)".formatted(Json.write(selector), Json.write(text));
        waitFor(view, find);
        js(view, find + ".click()");
    }

    @Test
    @Timeout(120)
    void eachAnswersJava() throws Exception {
        try (App app = App.start()) {
            Screen screen = new Screen();
            View view = Ui.open(app, screen, o -> o.headless(true).size(900, 1000)).join().view();
            until(view, "country ph");

            // combobox: type part of a name, Enter takes the match
            js(view, """
                    (() => { const i = document.querySelector('input[role=combobox]'); i.focus();
                      const set = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
                      set.call(i, 'jap'); i.dispatchEvent(new Event('input', {bubbles: true}));
                      i.dispatchEvent(new KeyboardEvent('keydown', {key: 'Enter', bubbles: true})); })()""");
            until(view, "country jp");

            // calendar: a click on a day gives its ISO date
            click(view, "button.nt-calendar-day:not([data-outside])", "18");
            until(view, "day 2026-11-18");

            // popover: a click on its trigger shows its content, Esc closes it
            click(view, "button", "Filters");
            until(view, "inside the popover");
            js(view, "window.dispatchEvent(new KeyboardEvent('keydown', {key: 'Escape'}))");
            waitFor(view, "!document.querySelector('.nt-popover')");

            // menubar: a menu opens under its title, an item runs Java
            click(view, "[role=menubar] button", "File");
            click(view, "[role^=menuitem]", "NewCtrl+N");
            until(view, "ran menu new");

            // command palette: Ctrl+K opens it, a click runs the command and closes it
            js(view, "window.dispatchEvent(new KeyboardEvent('keydown', {key: 'k', ctrlKey: true}))");
            for (int i = 0; i < 50 && !screen.palette; i++) Thread.sleep(100);
            assertTrue(screen.palette, "Ctrl+K opened it");
            click(view, "[role=option]", "Export");
            until(view, "ran command export");
            assertFalse(screen.palette, "running a command closed it");

            // sheet: opens over the window; Esc closes it
            click(view, "button", "Open sheet");
            until(view, "inside the sheet");
            js(view, "window.dispatchEvent(new KeyboardEvent('keydown', {key: 'Escape'}))");
            for (int i = 0; i < 50 && screen.sheetOpen; i++) Thread.sleep(100);
            assertFalse(screen.sheetOpen, "Esc closed the sheet");
        }
    }
}
