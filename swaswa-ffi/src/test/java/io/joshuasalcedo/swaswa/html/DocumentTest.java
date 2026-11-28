package io.joshuasalcedo.swaswa.html;

import io.joshuasalcedo.swaswa.css.CssProperty;
import io.joshuasalcedo.swaswa.css.Style;
import io.joshuasalcedo.swaswa.css.StyleSheet;
import static org.junit.jupiter.api.Assertions.*;

import io.joshuasalcedo.swaswa.ffi.App;
import io.joshuasalcedo.swaswa.ffi.Theme;
import io.joshuasalcedo.swaswa.ffi.View;
import io.joshuasalcedo.swaswa.ffi.WindowOptions;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** A view's page, live from Java: elements built and changed there, their events back, CSS, light and dark. */
@EnabledOnOs(OS.WINDOWS)
@Timeout(120)
class DocumentTest {

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
        return app.open(WindowOptions.html("<!doctype html><body>").title(title).size(500, 400)).join();
    }

    @Test
    void elementsAreBuiltInThePageAndChangeThere() throws Exception {
        try (App app = App.start()) {
            View view = page(app, "swaswa test: document");
            Document doc = Document.of(view);
            Span total = Html.span("0").setId("total").cssClass("total");
            Div box = Html.div(Html.h2("Total"), total).setId("box").style(CssProperty.DISPLAY, "flex");
            doc.body().add(box);
            doc.flush().join();
            assertEquals("<h2>Total</h2><span id=\"total\" class=\"total\">0</span>", js(view, "document.getElementById('box').innerHTML"));
            assertEquals("flex", js(view, "getComputedStyle(document.getElementById('box')).display"));

            total.setText("42");
            total.cssClass("big");
            box.style(CssProperty.DISPLAY, "grid");
            box.add(Html.em("first"), 0);
            doc.flush().join();
            assertEquals("42", js(view, "document.getElementById('total').textContent"));
            assertEquals("total big", js(view, "document.getElementById('total').className"));
            assertEquals("grid", js(view, "getComputedStyle(document.getElementById('box')).display"));
            assertEquals("EM", js(view, "document.getElementById('box').firstElementChild.tagName"), "added at the start");

            box.remove(total);
            doc.flush().join();
            assertEquals(Boolean.FALSE, js(view, "!!document.getElementById('total')"), "taken away");
        }
    }

    @Test
    void eventsComeToTheirListenersWithWhatThePageHolds() throws Exception {
        try (App app = App.start()) {
            View view = page(app, "swaswa test: document events");
            Document doc = Document.of(view);
            AtomicInteger clicks = new AtomicInteger();
            AtomicReference<String> typed = new AtomicReference<>("");
            AtomicReference<String> key = new AtomicReference<>("");
            Button add = Html.button("Add").setId("add");
            add.addActionListener(e -> clicks.incrementAndGet());
            Input name = Html.input().setId("name");
            name.addChangeListener(e -> typed.set(name.getValue()));   // held before the listener runs
            name.addKeyListener(e -> key.set(e.key()));
            Input agree = Html.input().setType(Input.Type.CHECKBOX).setId("agree");
            Form form = Html.form(Html.input().setName("city").setValue("Manila")).setId("form");
            AtomicReference<Object> sentCity = new AtomicReference<>();
            form.addSubmitListener(e -> sentCity.set(e.fields().get("city")));
            agree.addItemListener(e -> {});
            doc.body().add(add, name, agree, form);
            doc.flush().join();

            js(view, "document.getElementById('add').click(); document.getElementById('add').click()");
            assertEquals(2, (int) until(clicks::get, n -> n == 2));

            js(view, "const n = document.getElementById('name'); n.value = 'Ada'; n.dispatchEvent(new Event('input', {bubbles: true}));"
                    + " n.dispatchEvent(new KeyboardEvent('keydown', {key: 'Enter', bubbles: true}))");
            assertEquals("Ada", until(typed::get, "Ada"::equals));
            assertEquals("Enter", until(key::get, "Enter"::equals));

            js(view, "document.getElementById('agree').click()");
            assertTrue(until(agree::isChecked, Boolean.TRUE::equals), "checked, as the page has it");

            js(view, "document.getElementById('form').requestSubmit()");
            assertEquals("Manila", until(sentCity::get, "Manila"::equals), "its fields, and the page did not leave");
            assertEquals(Boolean.TRUE, js(view, "!!document.getElementById('add')"));

            name.setValue("Grace");
            doc.flush().join();
            assertEquals("Grace", js(view, "document.getElementById('name').value"), "set from Java: the page shows it");
        }
    }

    @Test
    void styleSheetsAreLiveAndFollowLightAndDark() throws Exception {
        try (App app = App.start()) {
            View view = page(app, "swaswa test: document css");
            Document doc = Document.of(view);
            Style card = Style.of().set(CssProperty.COLOR, Style.lightDark("rgb(10, 20, 30)", "rgb(200, 210, 220)"));
            StyleSheet sheet = new StyleSheet().rule(".card", card).dark(d -> d.rule(".card", s -> s.set(CssProperty.FONT_WEIGHT, "700")));
            doc.addStyleSheet(sheet);
            doc.body().add(Html.p("card").setId("c").cssClass("card"));

            doc.setTheme(Theme.LIGHT).join();
            assertEquals("rgb(10, 20, 30)", until(() -> js(view, "getComputedStyle(document.getElementById('c')).color"), "rgb(10, 20, 30)"::equals));
            doc.setTheme(Theme.DARK).join();
            assertEquals("rgb(200, 210, 220)", until(() -> js(view, "getComputedStyle(document.getElementById('c')).color"), "rgb(200, 210, 220)"::equals));
            assertEquals("700", until(() -> js(view, "getComputedStyle(document.getElementById('c')).fontWeight"), "700"::equals),
                    "the dark rules too (prefers-color-scheme follows the window's theme)");

            card.set(CssProperty.TEXT_TRANSFORM, "uppercase");   // a rule's style changed: the page has it
            doc.flush().join();
            assertEquals("uppercase", until(() -> js(view, "getComputedStyle(document.getElementById('c')).textTransform"), "uppercase"::equals));
            doc.removeStyleSheet(sheet);
            doc.flush().join();
            assertEquals("none", js(view, "getComputedStyle(document.getElementById('c')).textTransform"));
        }
    }

    @Test
    void aPageLoadedAgainHasItAllBack() throws Exception {
        try (App app = App.start()) {
            View view = page(app, "swaswa test: document reload");
            Document doc = Document.of(view);
            AtomicInteger clicks = new AtomicInteger();
            Button b = Html.button("Again").setId("again");
            b.addActionListener(e -> clicks.incrementAndGet());
            doc.body().add(b);
            doc.addStyleSheet(new StyleSheet().rule("#again", s -> s.set(CssProperty.MARGIN_LEFT, "33px")));
            doc.flush().join();
            js(view, "location.reload()");
            Thread.sleep(300);
            assertEquals("33px", until(() -> String.valueOf(js(view, "(document.getElementById('again') && getComputedStyle(document.getElementById('again')).marginLeft)")),
                    "33px"::equals), "the element and its sheet, back");
            assertEquals(1L, ((Number) js(view, "document.querySelectorAll('#again').length")).longValue(), "once");
            js(view, "document.getElementById('again').click()");
            assertEquals(1, (int) until(clicks::get, n -> n == 1), "and its listener");
        }
    }
}
