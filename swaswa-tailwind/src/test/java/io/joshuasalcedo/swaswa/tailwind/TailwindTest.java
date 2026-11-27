package io.joshuasalcedo.swaswa.tailwind;

import static org.junit.jupiter.api.Assertions.*;

import io.joshuasalcedo.swaswa.ffi.App;
import io.joshuasalcedo.swaswa.ffi.Theme;
import io.joshuasalcedo.swaswa.ffi.View;
import io.joshuasalcedo.swaswa.ffi.WindowOptions;
import io.joshuasalcedo.swaswa.html.Div;
import io.joshuasalcedo.swaswa.html.Document;
import io.joshuasalcedo.swaswa.html.Html;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** Tailwind's classes, typed: their names, their CSS as Tailwind writes it, their variants; and a page using them. */
@Timeout(60)
class TailwindTest {

    @Test
    void classesAreTypedAndTheirCssIsTailwinds() {
        assertEquals("p-4", Tw.P.P_4.className());
        assertEquals(".p-4 { padding: calc(var(--spacing) * 4); }", Tw.P.P_4.css());
        assertEquals("bg-red-500", Tw.Bg.BG_RED_500.className());
        assertEquals(".w-1\\/2 { width: calc(1 / 2 * 100%); }", Tw.of("w-1/2").css(), "a name's / escaped in its selector");
        assertEquals(".-m-4 { margin: calc(var(--spacing) * -4); }", Tw.of("-m-4").css());
        assertTrue(Tailwind.version().startsWith("4."), Tailwind.version());
        assertTrue(Tailwind.names().size() > 20_000, "every class: " + Tailwind.names().size());
    }

    @Test
    void variantsNestAsTailwind4WritesThem() {
        Utility hover = Tw.hover(Tw.Bg.BG_BLUE_500);
        assertEquals("hover:bg-blue-500", hover.className());
        assertEquals(".hover\\:bg-blue-500 { &:hover { @media (hover: hover) { background-color: var(--color-blue-500); } } }", hover.css());
        Utility wide = Tw.md(Tw.hover(Tw.P.P_8));
        assertEquals("md:hover:p-8", wide.className(), "the outer variant first, as Tailwind names it");
        assertEquals(".md\\:hover\\:p-8 { @media (width >= 48rem) { &:hover { @media (hover: hover) { padding: calc(var(--spacing) * 8); } } } }", wide.css());
        assertEquals(wide, Tw.of("md:hover:p-8"), "the same, by name");
        assertEquals(".dark\\:text-white { @media (prefers-color-scheme: dark) { color: var(--color-white); } }", Tw.dark(Tw.of("text-white")).css());
    }

    @Test
    void rulesSortAsTailwindsSoVariantsWin() {
        assertEquals(0, Tw.P.P_4.order(), "no variants: first");
        assertTrue(Tw.hover(Tw.P.P_4).order() > 0);
        assertTrue(Tw.md(Tw.P.P_4).order() > Tw.hover(Tw.P.P_4).order(), "breakpoints after states");
        assertTrue(Tw.lg(Tw.P.P_4).order() > Tw.md(Tw.P.P_4).order(), "small to large");
        assertTrue(Tw.dark(Tw.P.P_4).order() > Tw.xxl(Tw.P.P_4).order(), "dark after the breakpoints");
        assertTrue(Tw.md(Tw.hover(Tw.P.P_4)).order() > Tw.md(Tw.P.P_4).order(), "the outermost decides, then the next");
    }

    @Test
    void classesThatStyleTheirChildrenKeepTheirSelector() {
        assertTrue(Tw.of("space-x-4").css().startsWith(":where(.space-x-4 > :not(:last-child)) {"), Tw.of("space-x-4").css());
        String md = Tw.md(Tw.of("space-x-4")).css();
        assertTrue(md.startsWith("@media (width >= 48rem) { :where(.md\\:space-x-4 > :not(:last-child)) {"), md);
        String hover = Tw.hover(Tw.of("space-x-4")).css();
        assertTrue(hover.startsWith("@media (hover: hover) { :where(.hover\\:space-x-4:hover > :not(:last-child)) {"), hover);
    }

    @Test
    void namesAreChecked() {
        assertThrows(IllegalArgumentException.class, () -> Tw.of("p-banana"));
        assertThrows(IllegalArgumentException.class, () -> Tw.of("wobble:p-4"), "no such variant");
        assertEquals("group", Tw.GROUP.className());
        assertNull(Tw.GROUP.css(), "a marker: no CSS of its own");
    }

    // ---- in a page ----

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

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void aPageGetsTheCssOfTheClassesItUsesAndOnlyThose() throws Exception {
        try (App app = App.start()) {
            View view = app.open(WindowOptions.html("<!doctype html><body>").title("swaswa test: tailwind").size(1100, 600)).join();
            Document page = Document.of(view);
            Div card = Html.div("card").setId("card")
                    .cssClass(Tw.P.P_4, Tw.Rounded.ROUNDED_LG, Tw.Bg.BG_RED_500, Tw.md(Tw.P.P_8), Tw.dark(Tw.Bg.BG_BLUE_500));
            page.body().add(card);
            page.setTheme(Theme.LIGHT).join();
            String style = "getComputedStyle(document.getElementById('card'))";

            Object padding = until(() -> js(view, style + ".paddingTop"), "32px"::equals);
            assertEquals("32px", padding, () -> "md:p-8 at a 1100px wide window (p-4 below md); the page: " + js(view,
                    "({w: innerWidth, md: matchMedia('(width >= 48rem)').matches, cls: document.getElementById('card').className,"
                    + " sheets: [...document.querySelectorAll('style[data-swaswa-sheet]')].length, last: [...document.querySelectorAll('style[data-swaswa-sheet]')].pop().textContent.replace(/\s+/g, ' ').slice(-700)})"));
            assertEquals("8px", js(view, style + ".borderTopLeftRadius"), "rounded-lg: the theme's --radius-lg");
            String light = String.valueOf(js(view, style + ".backgroundColor"));
            assertTrue(light.contains("oklch") || light.startsWith("rgb"), "bg-red-500: " + light);

            page.setTheme(Theme.DARK).join();
            String dark = String.valueOf(until(() -> js(view, style + ".backgroundColor"), c -> !light.equals(c)));
            assertNotEquals(light, dark, "dark:bg-blue-500 when the window is dark");

            // only the classes used: their five rules, after the theme's sheet
            Object rules = js(view, "[...document.querySelectorAll('style[data-swaswa-sheet]')].map(s => s.textContent)");
            String all = String.valueOf(rules);
            assertTrue(all.contains("--spacing"), "the theme's variables, once");
            assertTrue(all.contains(".p-4 {") && all.contains(".md\\:p-8 {") && all.contains(".dark\\:bg-blue-500 {"));
            assertFalse(all.contains(".p-5 {") || all.contains(".bg-green-500 {"), "no class the page does not use");

            // a class added later: its rule comes then
            card.cssClass(Tw.Font.FONT_BOLD);
            page.flush().join();
            assertEquals("700", until(() -> js(view, style + ".fontWeight"), "700"::equals));
        }
    }
}
