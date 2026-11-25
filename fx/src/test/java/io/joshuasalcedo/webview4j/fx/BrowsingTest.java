package io.joshuasalcedo.webview4j.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class BrowsingTest {

    @Test
    void what_is_typed_in_the_address_bar_becomes_an_address() {
        assertEquals("https://example.com", Addresses.of("example.com"));
        assertEquals("https://en.wikipedia.org/wiki/WebView", Addresses.of("  https://en.wikipedia.org/wiki/WebView "));
        assertEquals("http://localhost:8080/", Addresses.of("localhost:8080/"));
        assertEquals("https://duckduckgo.com/?q=modern+webview+java", Addresses.of("modern webview java"));
        assertNull(Addresses.of("   "));
    }

    @Test
    void only_a_web_address_goes_to_another_browser() {
        assertEquals("https://example.com/a?b=c", ExternalBrowsers.checked("https://example.com/a?b=c"));
        for (String bad : new String[] {"file:///C:/Windows/win.ini", "--remote-debugging-port=9222", "javascript:alert(1)",
                "https://a.example/x y", "https://a.example/\"--flag"}) {
            assertThrows(IllegalArgumentException.class, () -> ExternalBrowsers.checked(bad), bad);
        }
    }
}
