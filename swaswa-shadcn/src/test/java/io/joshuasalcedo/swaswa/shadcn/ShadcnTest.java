package io.joshuasalcedo.swaswa.shadcn;

import static org.junit.jupiter.api.Assertions.*;

import io.joshuasalcedo.swaswa.ffi.App;
import io.joshuasalcedo.swaswa.ffi.View;
import io.joshuasalcedo.swaswa.ffi.WindowOptions;
import io.joshuasalcedo.swaswa.html.Document;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** shadcn/ui's real components, from Java: drawn and styled in the page, their events and values back. */
@EnabledOnOs(OS.WINDOWS)
@Timeout(120)
class ShadcnTest {

    static <T> T until(Supplier<T> value, Predicate<T> done) throws InterruptedException {
        T v = value.get();
        for (int i = 0; i < 100 && !done.test(v); i++) {
            Thread.sleep(50);
            v = value.get();
        }
        return v;
    }

    static Object js(View view, String script) {
        return view.eval(script).join();
    }

    static View page(App app, String title) {
        return app.open(WindowOptions.html("<!doctype html><body>").title(title).size(700, 500)).join();
    }

    @Test
    void aButtonIsShadcnsStyledAndItsClicksComeBack() throws Exception {
        try (App app = App.start()) {
            View view = page(app, "swaswa test: shadcn button");
            Document doc = Document.of(view);
            List<String> clicked = new CopyOnWriteArrayList<>();
            Button save = new Button("Save");
            save.setVariant(Button.Variant.DESTRUCTIVE);
            save.onClick(e -> clicked.add("save"));
            doc.body().add(save);
            doc.flush().join();

            String button = "document.querySelector('[data-slot=button]')";
            assertEquals("Save", until(() -> js(view, button + "?.textContent ?? ''"), "Save"::equals));
            assertNotEquals("rgba(0, 0, 0, 0)", js(view, "getComputedStyle(" + button + ").backgroundColor"),
                    "shadcn's CSS is in the page: a destructive button has its colour");
            assertEquals("BUTTON", js(view, button + ".tagName"));
            js(view, button + ".click()");
            assertEquals(List.of("save"), until(() -> clicked, l -> !l.isEmpty()));
        }
    }

    @Test
    void aSwitchIsKeptInJavaAndTabsShareTheirContext() throws Exception {
        try (App app = App.start()) {
            View view = page(app, "swaswa test: shadcn switch and tabs");
            Document doc = Document.of(view);
            Switch dark = new Switch();
            Tabs tabs = new Tabs(
                    new TabsList(new TabsTrigger("Open").setValue("open"), new TabsTrigger("Shipped").setValue("shipped")),
                    new TabsContent("three open orders").setValue("open"),
                    new TabsContent("ten shipped").setValue("shipped"));
            tabs.setValue("open");
            doc.body().add(dark);
            doc.body().add(tabs);
            doc.flush().join();

            String sw = "document.querySelector('[data-slot=switch]')";
            until(() -> js(view, "!!" + sw), Boolean.TRUE::equals);
            js(view, sw + ".click()");
            assertEquals(Boolean.TRUE, until(() -> dark.checkedProperty().get(), Boolean.TRUE::equals), "the person's click, in Java");
            dark.checkedProperty().set(false);
            doc.flush().join();
            assertEquals(Boolean.TRUE, until(() -> js(view, sw + ".hasAttribute('data-unchecked')"), Boolean.TRUE::equals), "Java's, in the page");

            String shown = "[...document.querySelectorAll('[data-slot=tabs-content]')].filter(p => !p.hidden).map(p => p.textContent).join()";
            assertEquals("three open orders", until(() -> js(view, shown), "three open orders"::equals),
                    "the tabs' context reaches its panels: only the chosen one shows");
            js(view, "[...document.querySelectorAll('[data-slot=tabs-trigger]')].find(t => t.textContent === 'Shipped').click()");
            assertEquals("shipped", until(() -> tabs.valueProperty().get(), "shipped"::equals), "the chosen tab, in Java");
            assertEquals("ten shipped", until(() -> js(view, shown), "ten shipped"::equals));
        }
    }

    @Test
    void aDialogOpensFromJava() throws Exception {
        try (App app = App.start()) {
            View view = page(app, "swaswa test: shadcn dialog");
            Document doc = Document.of(view);
            Dialog dialog = new Dialog(new DialogContent(
                    new DialogHeader(new DialogTitle("Delete the order?"), new DialogDescription("It cannot be undone."))));
            doc.body().add(dialog);
            doc.flush().join();
            Thread.sleep(300);
            assertEquals(Boolean.FALSE, js(view, "!!document.querySelector('[data-slot=dialog-content]')"), "closed");

            dialog.openProperty().set(true);
            doc.flush().join();
            assertEquals("Delete the order?", until(() -> js(view, "document.querySelector('[data-slot=dialog-title]')?.textContent ?? ''"),
                    "Delete the order?"::equals), "open: its title, Java's, in it");
        }
    }
}
