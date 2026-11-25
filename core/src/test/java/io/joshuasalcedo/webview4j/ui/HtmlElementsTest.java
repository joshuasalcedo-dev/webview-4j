package io.joshuasalcedo.webview4j.ui;

import static io.joshuasalcedo.webview4j.ui.ComponentsTest.until;
import static org.junit.jupiter.api.Assertions.*;

import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.View;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** HTML's elements from Java (Html.java, html-gen): all of MDN's current ones but those left out on purpose, drawn as themselves. */
class HtmlElementsTest {

    /** MDN's current elements (Web/HTML/Reference/Elements, read 2026-11-25). */
    static final List<String> MDN = List.of(("html base head link meta style title body address article aside footer header h1 h2 h3 h4 h5 h6 "
            + "hgroup main nav section search blockquote dd div dl dt figcaption figure hr li menu ol p pre ul a abbr b bdi bdo br cite code "
            + "data dfn em i kbd mark q rp rt ruby s samp small span strong sub sup time u var wbr area audio img map track video embed "
            + "iframe object picture source svg math canvas noscript script del ins caption col colgroup table tbody td tfoot th thead tr "
            + "button datalist fieldset form input label legend meter optgroup option output progress select selectedcontent textarea "
            + "details dialog geolocation summary slot template").split(" "));
    /** Left out on purpose: the page's own frame, what runs code or loads another site, style (styleSheet). */
    static final Set<String> LEFT_OUT = Set.of("html", "head", "body", "base", "meta", "link", "title", "style", "script", "noscript", "iframe", "object", "embed");
    /** SVG and MathML: their own element sets; and the two React's types do not have yet (both experimental). */
    static final Set<String> NOT_HTML_YET = Set.of("svg", "math", "selectedcontent", "geolocation");

    @Test
    void everyCurrentElementIsThereButThoseLeftOutOnPurpose() {
        Set<String> made = Arrays.stream(Html.class.getDeclaredClasses())
                .filter(c -> HtmlElement.class.isAssignableFrom(c))
                .map(c -> c.getSimpleName().toLowerCase())
                .collect(Collectors.toSet());
        List<String> missing = MDN.stream().filter(t -> !LEFT_OUT.contains(t) && !NOT_HTML_YET.contains(t) && !made.contains(t)).toList();
        assertTrue(missing.isEmpty(), "MDN's current elements without a class in Html: " + missing);
        for (String t : LEFT_OUT) assertFalse(made.contains(t), t + " is left out on purpose");
        assertEquals(99, made.size());
    }

    @Test
    void attributesAreTypedAndUrlsChecked() {
        Html.Input input = Html.input().type(Html.Input.Type.EMAIL).required().placeholder("you@example.com").maxLength(80);
        @SuppressWarnings("unchecked")
        Map<String, Object> a = (Map<String, Object>) ((Map<String, Object>) GeneratedComponentsTest.tree(input).get("p")).get("a");
        assertEquals(Map.of("type", "email", "required", true, "placeholder", "you@example.com", "maxLength", 80.0), a);
        Html.a("Docs").href("https://example.com").href("/help").href("#top").href("mailto:ana@example.com");
        assertThrows(IllegalArgumentException.class, () -> Html.a("x").href("javascript:alert(1)"));
        assertThrows(IllegalArgumentException.class, () -> Html.a("x").href(" JavaScript:alert(1)"));
        assertThrows(IllegalArgumentException.class, () -> Html.img().src("data:text/html,<script>alert(1)</script>"));
        Html.img().src("data:image/png;base64,iVBORw0K").alt("A dot");
    }

    static final class Page implements io.joshuasalcedo.webview4j.ui.Screen {
        volatile Map<String, Object> sent = Map.of();
        volatile String typed = "";
        volatile int clicks;

        @Override
        public Component<?> render() {
            return Html.main(
                    Html.h1("Invoice 42").id("title"),
                    Html.p(Html.text("Paid on "), Html.strong("Monday"), Html.text(".")).id("paid"),
                    Html.ul(Html.li("Design"), Html.li("Build")).id("list"),
                    Html.form(
                            Html.label("E-mail").htmlFor("email"),
                            Html.input().type(Html.Input.Type.EMAIL).name("email").id("email").onInput(v -> typed = v),
                            Html.input().type(Html.Input.Type.CHECKBOX).name("topic").value("news").checked(true),
                            Html.input().type(Html.Input.Type.CHECKBOX).name("topic").value("offers").checked(true),
                            Html.button("Send").type(Html.Button.Type.SUBMIT).id("send")).onSubmit(fields -> sent = fields),
                    Html.button("Clicked " + clicks).id("click").onClick(() -> clicks++),
                    Html.a("Out").href("javascript:alert(1)".replace("javascript:", "https://example.com/#")).id("out"),
                    Ui.button("A kit button inside HTML")).style(Style.of().padding(16));
        }
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    @Timeout(90)
    void theyAreDrawnAsThemselvesAndAnswerJava() throws Exception {
        try (App app = App.start()) {
            Page page = new Page();
            View view = Ui.open(app, page, o -> o.headless(true).size(700, 600)).join().view();
            until(view, "Invoice 42");
            assertEquals("H1 P LI MAIN", view.eval("[document.getElementById('title').tagName, document.getElementById('paid').tagName, "
                    + "document.querySelector('#list li').tagName, document.getElementById('title').parentElement.tagName].join(' ')").join());
            assertEquals("Paid on Monday.", view.eval("document.getElementById('paid').textContent").join());
            assertEquals("email", view.eval("document.getElementById('email').type").join());
            assertTrue(String.valueOf(view.eval("document.body.innerText").join()).contains("A kit button inside HTML"), "kit components inside HTML");

            view.eval("document.getElementById('click').click()").join();
            until(view, "Clicked 1");

            view.eval("""
                    (() => { const i = document.getElementById('email'); i.focus();
                      const set = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
                      set.call(i, 'ana@example.com'); i.dispatchEvent(new Event('input', {bubbles: true})); })()""").join();
            for (int i = 0; i < 50 && page.typed.isEmpty(); i++) Thread.sleep(100);
            assertEquals("ana@example.com", page.typed);

            Object before = view.eval("location.href").join();
            view.eval("document.getElementById('send').click()").join();
            for (int i = 0; i < 50 && page.sent.isEmpty(); i++) Thread.sleep(100);
            assertEquals("ana@example.com", page.sent.get("email"));
            assertEquals(List.of("news", "offers"), page.sent.get("topic"), "a name given twice comes as a list");
            Thread.sleep(300);
            assertEquals(before, view.eval("location.href").join(), "the form did not leave the app's page");
        }
    }
}
