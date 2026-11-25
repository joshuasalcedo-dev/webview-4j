package io.joshuasalcedo.webview4j.sampler.pages;

import static org.junit.jupiter.api.Assertions.*;

import io.joshuasalcedo.webview4j.sampler.SamplerScreen;
import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.View;
import io.joshuasalcedo.webview4j.ui.Ui;
import io.joshuasalcedo.webview4j.ui.UiWindow;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * The gallery shows every component: each component class is covered by a page, and each page draws in a real
 * window without a component the page cannot show. Only one category: {@code -Dsampler.section=Layout}.
 */
class SamplerTest {

    @Test
    void everyComponentHasADemo() {
        List<Class<?>> missing = new Gallery().missing();
        assertTrue(missing.isEmpty(), "no gallery page covers: " + missing.stream().map(Class::getName).toList());
    }

    @Test
    void aPageCoversOnlyComponents() {
        List<Class<?>> all = Gallery.components();
        assertTrue(all.size() > 50, "the component classes were found: " + all.size());
        for (Gallery.Page p : new Gallery().pages()) {
            for (Class<?> c : p.covers()) assertTrue(all.contains(c), p.name() + " covers " + c.getName() + ", not a component");
        }
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    @Timeout(600)
    void everyPageDraws() throws Exception {
        String only = System.getProperty("sampler.section", "");
        try (App app = App.start()) {
            SamplerScreen screen = new SamplerScreen();
            UiWindow window = Ui.open(app, screen, o -> o.headless(true).size(1180, 900)).join();
            View view = window.view();
            List<String> broken = new ArrayList<>();
            for (Gallery.Page page : screen.gallery().pages()) {
                if (!only.isEmpty() && !page.section().equals(only)) continue;
                screen.show(page.name());
                window.refresh().join();
                String text = "";
                for (int i = 0; i < 50; i++) {
                    text = String.valueOf(view.eval("document.body.innerText").join());
                    if (text.contains("The Java") && text.contains(page.name())) break;
                    Thread.sleep(100);
                }
                if (!text.contains("The Java")) broken.add(page.name() + ": the page did not draw");
                else if (text.contains("No component \"")) broken.add(page.name() + ": " + text.substring(text.indexOf("No component \"")).lines().findFirst().orElse(""));
            }
            assertTrue(broken.isEmpty(), "pages that do not draw: " + broken);
        }
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    @Timeout(900)
    void everyPageDrawsOnTheWebToo() throws Exception {
        String only = System.getProperty("sampler.section", "");
        try (App app = App.start();
             io.joshuasalcedo.webview4j.ui.WebHost host = io.joshuasalcedo.webview4j.ui.WebHost.of(SamplerScreen::new).title("Sampler").start()) {
            View view = app.open(io.joshuasalcedo.webview4j.WindowOptions.url(host.url().toString()).headless(true).size(1180, 900)).join();
            List<String> broken = new ArrayList<>();
            for (Gallery.Page page : new Gallery().pages()) {
                if (!only.isEmpty() && !page.section().equals(only)) continue;
                // as a person would: the page's item in the side bar
                String name = page.name().replace("\\", "\\\\").replace("'", "\\'");
                String text = "";
                for (int i = 0; i < 60; i++) {
                    // its category opens first when the page is under one
                    String section = page.section().replace("'", "\\'");
                    view.eval("(() => { const all = [...document.querySelectorAll('button')]; const b = all.find(e => e.innerText.trim() === '" + name + "');"
                            + " if (b) { if (b.getAttribute('aria-current') !== 'page') b.click(); return; }"
                            + " const c = all.find(e => e.innerText.trim() === '" + section + "' && e.getAttribute('aria-expanded') === 'false'); if (c) c.click(); })()").join();
                    text = String.valueOf(view.eval("document.body ? document.body.innerText : ''").join());
                    if (text.contains("The Java") && text.contains(page.name())
                            && Boolean.TRUE.equals(view.eval("[...document.querySelectorAll('button[aria-current=page]')].some(b => b.innerText.trim() === '" + name + "')").join())) break;
                    Thread.sleep(100);
                }
                if (!text.contains("The Java")) broken.add(page.name() + ": the page did not draw on the web");
                else if (text.contains("No component \"")) broken.add(page.name() + ": " + text.substring(text.indexOf("No component \"")).lines().findFirst().orElse(""));
            }
            assertTrue(broken.isEmpty(), "pages that do not draw on the web: " + broken);
        }
    }
}
