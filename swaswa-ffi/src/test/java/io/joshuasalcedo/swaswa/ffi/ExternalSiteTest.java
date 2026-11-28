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

            // browse(): a link to any website opens in the window itself (without it, in the system's browser)
            View browser = app.open(WindowOptions.html("<a id=a href='https://example.com/'>out</a>").headless(true).browse()).join();
            until(browser, "document.getElementById('a') !== null", true);   // its page drawn, the link there
            browser.eval("document.getElementById('a').click()").join();
            assertEquals("Example Domain", until(browser, "document.title", "Example Domain"), "browsed to in place");

            // the site's network calls, listened to once it is open
            var calls = new java.util.concurrent.LinkedBlockingQueue<NetworkEvent>();
            app.subscribe(NetworkEvent.class, calls::add);
            injected.capture(Capture.NETWORK, true).join();
            injected.eval("fetch('/?swaswa=1').then(r => r.status)").join();
            NetworkEvent started = null, done = null;
            for (int i = 0; i < 50 && done == null; i++) {
                NetworkEvent e = calls.poll(300, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (e == null) continue;
                if (e.phase() == NetworkEvent.Phase.START && e.url() != null && e.url().contains("swaswa=1")) started = e;
                if (started != null && e.phase() == NetworkEvent.Phase.END && e.id() == started.id()) done = e;
            }
            assertNotNull(started, "the fetch was heard starting");
            assertEquals("GET", started.method());
            assertNotNull(done, "and ending");
            assertTrue(done.status() > 0, "with its status: " + done.status());
        }
    }
}
