package io.joshuasalcedo.swaswa.tailwind;

import static org.junit.jupiter.api.Assertions.*;

import io.joshuasalcedo.swaswa.css.StyleSheet;
import io.joshuasalcedo.swaswa.ffi.App;
import io.joshuasalcedo.swaswa.ffi.View;
import io.joshuasalcedo.swaswa.ffi.WindowOptions;
import io.joshuasalcedo.swaswa.html.Button;
import io.joshuasalcedo.swaswa.html.Div;
import io.joshuasalcedo.swaswa.html.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** A CSS file the Tailwind way: @apply, @theme's tokens and their classes, @utility; the basics, typed. */
@Timeout(60)
class TailwindCssTest {

    final TailwindProcessor tailwind = new TailwindProcessor();

    @Test
    void applyPutsTheClassesCssInItsPlaceVariantsNested() {
        String css = tailwind.process(".btn { @apply px-4 hover:bg-indigo-500; }");
        assertEquals(".btn { padding-inline: calc(var(--spacing) * 4); &:hover { @media (hover: hover) { background-color: var(--color-indigo-500); } } }", css);
    }

    @Test
    void themeTokensAreVariablesAndMakeClasses() {
        String css = tailwind.process("@theme { --color-brand: #4f46e5; } .hero { @apply bg-brand text-brand; }");
        assertTrue(css.contains(":root, :host { --color-brand: #4f46e5; }"), css);
        assertTrue(css.contains("background-color: var(--color-brand);"), "bg-brand, made from the token: " + css);
        assertTrue(css.contains("color: var(--color-brand);"), "text-brand too: " + css);
    }

    @Test
    void anAppsOwnUtility() {
        assertEquals(" .code { tab-size: 4; }", tailwind.process("@utility tab-4 { tab-size: 4; } .code { @apply tab-4; }"));
    }

    @Test
    void whatCannotBeAppliedSaysWhy() {
        IllegalArgumentException unknown = assertThrows(IllegalArgumentException.class, () -> tailwind.process(".x { @apply p-banana; }"));
        assertTrue(unknown.getMessage().contains("p-banana"), unknown.getMessage());
        IllegalArgumentException children = assertThrows(IllegalArgumentException.class, () -> tailwind.process(".x { @apply space-x-4; }"));
        assertTrue(children.getMessage().contains("children"), children.getMessage());
    }

    @Test
    void theBasicsAreTypedAndBringTheirCss() {
        assertEquals("btn-primary", Basic.BTN_PRIMARY.className());
        assertTrue(Basic.CARD.css().startsWith(".card {"), Basic.CARD.css());
        assertFalse(Basic.CARD.css().contains("@apply"), "Tailwind's CSS in it");
        assertTrue(Basic.CARD.order() < Tw.P.P_4.order(), "a utility on the same element wins");
    }

    @Test
    void aLoadedFileGoesThroughTailwindFoundOnTheClassPath() {
        StyleSheet sheet = StyleSheet.load("showcase-test.css");
        String css = sheet.toCss();
        assertFalse(css.contains("@apply") || css.contains("@import"), "imports followed, @apply done");
        assertTrue(css.contains(".btn-primary {") && css.contains(".hero {"), "the basics and the app's own");
        assertEquals(1, sheet.requires().size(), "Tailwind's variables, before it");
    }

    // ---- in a page ----

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void aPageWithTheFileAndTheTypedBasics() throws Exception {
        try (App app = App.start()) {
            View view = app.open(WindowOptions.html("<!doctype html><body>").title("swaswa test: tailwind css").size(800, 500)).join();
            Document page = Document.of(view);
            page.addStyleSheet(StyleSheet.load("showcase-test.css"));

            Div hero = new Div("hero");
            hero.setId("hero");
            hero.cssClass("hero");

            Button save = new Button("Save");
            save.setId("save");
            save.cssClass(Basic.BTN_PRIMARY);

            page.body().add(hero, save);
            page.flush().join();

            String style = "getComputedStyle(document.getElementById('hero'))";
            assertEquals("rgb(79, 70, 229)", TailwindTest.until(() -> view.eval(style + ".backgroundColor").join(), "rgb(79, 70, 229)"::equals),
                    "bg-brand: the app's own token");
            assertEquals("16px", view.eval(style + ".paddingTop").join());
            assertEquals("16px", view.eval("getComputedStyle(document.getElementById('save')).paddingLeft").join(), "btn-primary: px-4");
        }
    }
}
