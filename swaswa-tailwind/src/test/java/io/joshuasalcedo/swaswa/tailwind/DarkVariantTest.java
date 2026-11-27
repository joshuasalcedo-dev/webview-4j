package io.joshuasalcedo.swaswa.tailwind;

import static org.junit.jupiter.api.Assertions.*;

import io.joshuasalcedo.swaswa.ffi.App;
import io.joshuasalcedo.swaswa.ffi.Theme;
import io.joshuasalcedo.swaswa.ffi.View;
import io.joshuasalcedo.swaswa.ffi.WindowOptions;
import io.joshuasalcedo.swaswa.html.Div;
import io.joshuasalcedo.swaswa.html.Document;
import io.joshuasalcedo.swaswa.html.Html;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** A dark: variant wins over its plain class whatever else the element has (a card's many classes). */
@EnabledOnOs(OS.WINDOWS)
class DarkVariantTest {

    @Test
    void aCardsDarkBackgroundWins() throws Exception {
        try (App app = App.start()) {
            View view = app.open(WindowOptions.html("<!doctype html><body>").title("swaswa test: card").size(800, 500)).join();
            Document page = Document.of(view);
            Div card = Html.div("card").setId("card").cssClass(Tw.Rounded.ROUNDED_XL, Tw.P.P_6, Tw.Bg.BG_WHITE, Tw.dark(Tw.Bg.BG_SLATE_900),
                    Tw.Shadow.SHADOW_SM, Tw.Ring.RING_1, Tw.Ring.RING_SLATE_200, Tw.dark(Tw.Ring.RING_SLATE_800), Tw.Transition.TRANSITION,
                    Tw.hover(Tw.Shadow.SHADOW_LG), Tw.hover(Tw.Translate.NEG_TRANSLATE_Y_1));
            page.body().add(card);
            page.setTheme(Theme.DARK).join();
            Thread.sleep(800);
            Object bg = view.eval("getComputedStyle(document.getElementById('card')).backgroundColor").join();
            Object sheet = view.eval("[...document.querySelectorAll('style[data-swaswa-sheet]')].pop().textContent").join();
            assertNotEquals("rgb(255, 255, 255)", bg, "dark: the card's background; the rules: " + sheet);
        }
    }
}
