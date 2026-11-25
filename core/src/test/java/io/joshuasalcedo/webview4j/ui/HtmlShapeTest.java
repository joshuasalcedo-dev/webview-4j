package io.joshuasalcedo.webview4j.ui;

import static io.joshuasalcedo.webview4j.ui.ComponentsTest.until;
import static io.joshuasalcedo.webview4j.ui.Ui.*;
import static org.junit.jupiter.api.Assertions.*;

import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.Json;
import io.joshuasalcedo.webview4j.View;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * The shape of HTML on any component (Component's id, className, attributes, style), on its outermost element in
 * the page; changed at runtime, the old taken off; the app's own CSS reaching its classes.
 */
class HtmlShapeTest {

    @Test
    void theSettingsAreCheckedAndKeptApartFromTheComponentsOwnProps() {
        Component<?> b = button("Save").id("save").className("primary", "big").attr("title", "Saves it")
                .dataAttribute("row", "42").ariaAttribute("keyshortcuts", "Control+S")
                .style(Style.of().padding(8).background(Style.Token.ACCENT).radius(6)).css("letter-spacing", "0.02em");
        @SuppressWarnings("unchecked") Map<String, Object> p = (Map<String, Object>) GeneratedComponentsTest.tree(b).get("p");
        assertEquals("Save", p.get("label"), "its own props as they were");
        @SuppressWarnings("unchecked") Map<String, Object> html = (Map<String, Object>) p.get("html");
        assertEquals("save", html.get("id"));
        assertEquals(java.util.List.of("primary", "big"), html.get("className"));
        assertEquals(Map.of("title", "Saves it", "data-row", "42", "aria-keyshortcuts", "Control+S"), html.get("attrs"));
        assertEquals("var(--nt-accent)", ((Map<?, ?>) html.get("style")).get("background"));

        // what could run code or break out is refused
        assertThrows(IllegalArgumentException.class, () -> button("x").attr("onclick", "alert(1)"));
        assertThrows(IllegalArgumentException.class, () -> button("x").attr("href", "javascript:alert(1)"));
        assertThrows(IllegalArgumentException.class, () -> button("x").css("background", "url(javascript:alert(1))"));
        assertThrows(IllegalArgumentException.class, () -> button("x").css("color", "red; position: fixed"));
        assertThrows(IllegalArgumentException.class, () -> button("x").css("color", "expression(alert(1))"));
        assertThrows(IllegalArgumentException.class, () -> button("x").className("a b"));
        assertThrows(IllegalArgumentException.class, () -> button("x").dataAttribute("Row", "1"));
        assertThrows(IllegalArgumentException.class, () -> styleSheet(".a{}</style><script>alert(1)</script>"));
        button("x").css("background", "url(app/paper.png)");   // the app's own files are fine
    }

    /** MDN's global attributes (Web/HTML/Reference/Global_attributes, read 2026-11-25), class and style aside. */
    static final java.util.List<String> MDN = java.util.List.of(
            "accesskey", "anchor", "autocapitalize", "autocorrect", "autofocus", "containertiming", "containertimingignore",
            "contenteditable", "dir", "draggable", "enterkeyhint", "exportparts", "headingoffset", "headingreset", "hidden",
            "id", "inert", "inputmode", "is", "itemid", "itemprop", "itemref", "itemscope", "itemtype", "lang", "nonce",
            "part", "popover", "role", "slot", "spellcheck", "tabindex", "title", "translate", "virtualkeyboardpolicy",
            "writingsuggestions");

    @SuppressWarnings("unchecked")
    private static Map<String, Object> attrs(Component<?> c) {
        return (Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>) GeneratedComponentsTest.tree(c).get("p")).get("html")).get("attrs");
    }

    @Test
    void everyGlobalAttributeOnMdnIsThereByNameAndTyped() {
        for (String name : MDN) button("x").attr(name, "");   // by name: none refused
        Map<String, Object> typed = attrs(button("x")
                .accessKey("s").anchor("menu").autoCapitalize(Attr.AutoCapitalize.WORDS).autoCorrect(false).autofocus()
                .containerTiming("hero").containerTimingIgnore(true).contentEditable(Attr.ContentEditable.PLAINTEXT_ONLY)
                .dir(Attr.Dir.RTL).draggable(true).enterKeyHint(Attr.EnterKeyHint.SEND).exportParts("label, icon: badge")
                .htmlHeadingOffset(2).headingReset(true).hidden(true).inert(true).inputMode(Attr.InputMode.EMAIL)
                .isElement("fancy-button").itemId("urn:isbn:0-330-34032-8").itemProp("name").itemRef("a", "b")
                .itemType("https://schema.org/Book").lang("fil-PH").nonce("r4nd0m+==").part("label").popover(Attr.Popover.MANUAL)
                .role("status").slotName("footer").spellCheck(false).tabIndex(0).htmlTitle("Saves it").translate(false)
                .virtualKeyboardPolicy(Attr.VirtualKeyboardPolicy.MANUAL).writingSuggestions(false));
        for (String name : MDN) {
            if (name.equals("id")) continue;   // id() has its own place (html.id)
            assertTrue(typed.containsKey(name), "a typed method sets " + name);
        }
        assertEquals("rtl", typed.get("dir"));
        assertEquals("plaintext-only", typed.get("contenteditable"));
        assertEquals("no", typed.get("translate"));
        assertEquals("off", typed.get("autocorrect"));
        assertEquals("", typed.get("itemscope"), "itemtype makes it an item too");
        // and what is not one is refused, with where to look instead
        assertThrows(IllegalArgumentException.class, () -> button("x").attr("onclick", "x"));
        assertThrows(IllegalArgumentException.class, () -> button("x").attr("class", "x"));
        assertThrows(IllegalArgumentException.class, () -> button("x").htmlHeadingOffset(9));
        assertThrows(IllegalArgumentException.class, () -> button("x").lang("not a tag"));
        assertThrows(IllegalArgumentException.class, () -> button("x").itemType("javascript:alert(1)"));
        assertThrows(IllegalArgumentException.class, () -> button("x").accessKey("ab"));
    }

    static final class Attributes implements Screen {
        @Override
        public Component<?> render() {
            return column(text("مرحبا").dir(Attr.Dir.RTL).lang("ar").id("rtl"),
                    surface("Book").itemType("https://schema.org/Book").id("book"),
                    text("Kept as is").translate(false).id("keep")).padding(16);
        }
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    @Timeout(90)
    void theyReachThePage() throws Exception {
        try (App app = App.start()) {
            View view = Ui.open(app, new Attributes(), o -> o.headless(true).size(500, 300)).join().view();
            until(view, "Kept as is");
            assertEquals("rtl ar", view.eval("(() => { const e = document.getElementById('rtl'); return e.dir + ' ' + e.lang; })()").join());
            assertEquals("https://schema.org/Book true", view.eval("(() => { const e = document.getElementById('book'); return e.getAttribute('itemtype') + ' ' + e.hasAttribute('itemscope'); })()").join());
            assertEquals("no", view.eval("document.getElementById('keep').getAttribute('translate')").join());
        }
    }

    static final class Hovering implements Screen {
        volatile String kit = "";
        volatile String element = "";

        @Override
        public Component<?> render() {
            return column(
                    button("Hover me").id("kit")
                            .hoverStyle(Style.of().background(Style.Token.ACCENT).color(Style.Token.ON_ACCENT))
                            .focusStyle(Style.of().border(2, Style.Token.ACCENT)).pressedStyle(Style.of().opacity(0.7))
                            .onHover(on -> kit = on ? "in" : "out"),
                    Html.div("An HTML box").id("box").hoverStyle(Style.of().elevation(2)).onHover(on -> element = on ? "in" : "out"),
                    text("kit " + kit + " element " + element)).padding(16);
        }
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    @Timeout(90)
    void stateStylesAreThePagesOwnAndHoverReachesJava() throws Exception {
        try (App app = App.start()) {
            Hovering screen = new Hovering();
            View view = Ui.open(app, screen, o -> o.headless(true).size(500, 300)).join().view();
            until(view, "An HTML box");
            // the rule is the page's: a class on the button itself, the rule for :hover, :focus-within and :active
            String rules = String.valueOf(view.eval("""
                    (() => { const b = document.getElementById('kit'); const c = [...b.classList].find(x => x.startsWith('nt-s-'));
                      const css = [...document.querySelectorAll('style')].map(s => s.textContent).join('\\n');
                      return b.tagName + ' ' + (css.includes('.' + c + ':hover{background:var(--nt-accent) !important')) + ' '
                        + css.includes('.' + c + ':focus-within{') + ' ' + css.includes('.' + c + ':active{opacity:0.7 !important'); })()""").join());
            assertEquals("BUTTON true true true", rules);
            // onHover: the pointer coming on and leaving, for a kit component and an HTML element
            view.eval("document.getElementById('kit').dispatchEvent(new PointerEvent('pointerenter'))").join();
            until(view, "kit in");
            view.eval("document.getElementById('kit').dispatchEvent(new PointerEvent('pointerleave'))").join();
            until(view, "kit out");
            view.eval("document.getElementById('box').dispatchEvent(new PointerEvent('pointerover', {bubbles: true}))").join();
            until(view, "element in");
        }
    }

    static final class Shaped implements Screen {
        volatile boolean dirty;

        @Override
        public Component<?> render() {
            return column(
                    styleSheet(".unsaved { outline: 3px solid rgb(255, 0, 0); }"),
                    button("Save").id("save").dataAttribute("state", dirty ? "dirty" : "clean")
                            .className(dirty ? "unsaved" : "saved")
                            .style(dirty ? Style.of().padding(20).radius(12) : Style.of().radius(2))
                            .onClick(() -> dirty = !dirty),
                    text("hidden text").hidden(true),
                    text("state " + (dirty ? "dirty" : "clean"))).padding(16);
        }
    }

    private static Map<String, Object> look(View view) {
        return Json.object(String.valueOf(view.eval("""
                JSON.stringify((() => { const b = document.getElementById('save'); const s = getComputedStyle(b);
                  const h = [...document.querySelectorAll('*')].find(e => e.textContent === 'hidden text' && !e.children.length);
                  return { tag: b.tagName, classes: [...b.classList], state: b.dataset.state, padding: s.paddingTop,
                    radius: s.borderTopLeftRadius, outline: s.outlineColor + ' ' + s.outlineWidth,
                    hiddenShown: h ? h.getBoundingClientRect().height > 0 : null }; })())""").join()));
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    @Timeout(90)
    void theyReachTheOutermostElementAndChangeAtRuntime() throws Exception {
        try (App app = App.start()) {
            Shaped screen = new Shaped();
            View view = Ui.open(app, screen, o -> o.headless(true).size(600, 400)).join().view();
            until(view, "state clean");

            Map<String, Object> clean = look(view);
            assertEquals("BUTTON", clean.get("tag"), "on the component's own element, not a wrapper");
            assertTrue(((java.util.List<?>) clean.get("classes")).contains("saved"));
            assertTrue(((java.util.List<?>) clean.get("classes")).contains("nt-button"), "its own classes kept");
            assertEquals("clean", clean.get("state"));
            assertEquals("2px", clean.get("radius"));
            assertEquals(false, clean.get("hiddenShown"), "hidden is hidden, whatever the kit's display");

            // at runtime: the handler changes Java's field, the screen draws again, the old settings come off
            view.eval("document.getElementById('save').click()").join();
            until(view, "state dirty");
            Map<String, Object> dirty = look(view);
            assertEquals("dirty", dirty.get("state"));
            assertTrue(((java.util.List<?>) dirty.get("classes")).contains("unsaved"));
            assertFalse(((java.util.List<?>) dirty.get("classes")).contains("saved"), "the old class came off");
            assertEquals("20px", dirty.get("padding"));
            assertEquals("12px", dirty.get("radius"));
            String outline = String.valueOf(dirty.get("outline"));   // e.g. "rgb(255, 0, 0) 2.72727px": display scaling rounds the 3 px
            assertTrue(outline.startsWith("rgb(255, 0, 0) ") && Double.parseDouble(outline.replaceAll(".* ([0-9.]+)px", "$1")) > 2, "the app's own CSS reaches its class: " + outline);

            view.eval("document.getElementById('save').click()").join();
            until(view, "state clean");
            Map<String, Object> back = look(view);
            assertEquals("2px", back.get("radius"));
            assertFalse(((java.util.List<?>) back.get("classes")).contains("unsaved"));
            assertNotEquals("20px", back.get("padding"), "the old padding came off");
        }
    }
}
