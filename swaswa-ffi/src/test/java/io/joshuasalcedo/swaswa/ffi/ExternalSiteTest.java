package io.joshuasalcedo.swaswa.ffi;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * An external site in a webview of the app's own window, and CSS and script put into it from Java. Reaches the
 * internet (example.com): run with -Dswaswa.online=true.
 */
@EnabledOnOs(OS.WINDOWS)
@EnabledIfSystemProperty(named = "swaswa.online", matches = "true")
class ExternalSiteTest {

    static Object until(View view, String script, Object wanted) throws InterruptedException {
        Object got = null;
        for (int i = 0; i < 150; i++) {
            got = view.eval(script).join();
            if (wanted.equals(got)) return got;
            Thread.sleep(100);
        }
        return got;
    }

    @Test
    @Timeout(90)
    void anExternalSiteIsEmbeddedAndChangedFromJava() throws Exception {
        try (App app = App.start()) {
            View host = app.open(WindowOptions.html("<body style='margin:0'><nav style='height:46px'>our own UI</nav>").headless(true).size(900, 600)).join();
            View site = host.embed(WindowOptions.url("https://example.com/"), 0, 46, 900, 554).join();
            until(site, "document.title", "Example Domain");
            assertEquals(true, until(site, "document.querySelector('body p') !== null", true), "the external site, embedded (its text drawn)");
            site.place(0, 46, 700, 400).join();

            Object length = site.eval("""
                    (() => { const s = document.createElement('style'); s.textContent = 'p { color: rgb(1, 2, 3) }';
                      document.head.append(s); return s.textContent.length; })()""").join();
            assertEquals("p { color: rgb(1, 2, 3) }".length(), ((Number) length).intValue());
            assertEquals("rgb(1, 2, 3)", site.eval("getComputedStyle(document.querySelector('body p')).color").join(), "the injected CSS applies");
            assertEquals("Example Domain", site.eval("document.title").join(), "a script's answer comes back to Java");

            View injected = app.open(WindowOptions.url("https://example.com/").headless(true).size(800, 500)
                    .initializationScript("window.__swaswa = 'before the page'")).join();
            assertEquals("before the page", until(injected, "window.__swaswa ?? null", "before the page"), "the initialization script ran");
        }
    }
}
