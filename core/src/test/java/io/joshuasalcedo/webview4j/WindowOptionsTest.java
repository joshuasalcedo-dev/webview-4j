package io.joshuasalcedo.webview4j;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

class WindowOptionsTest {

    @Test
    void ownerWindowGoesIntoThePayloadOnlyWhenSet() {
        assertFalse(WindowOptions.blank().toPayload().containsKey("ownerWindow"));
        assertEquals(0x1234L, WindowOptions.blank().ownerWindow(0x1234).toPayload().get("ownerWindow"));
        assertThrows(IllegalArgumentException.class, () -> WindowOptions.blank().ownerWindow(0));
    }

    @Test
    void windowStateOptionsAreSentOnlyWhenSet() {
        Map<String, Object> plain = WindowOptions.blank().toPayload();
        for (String key : new String[] {"minWidth", "minHeight", "maximized", "fullscreen", "alwaysOnTop", "confirmClose", "fileDrop"}) {
            assertFalse(plain.containsKey(key), key);
        }
        Map<String, Object> set = WindowOptions.blank().minSize(400, 300).maximized(true).fullscreen(true)
                .alwaysOnTop(true).confirmClose(true).fileDrop(true).toPayload();
        assertEquals(400.0, set.get("minWidth"));
        assertEquals(300.0, set.get("minHeight"));
        for (String key : new String[] {"maximized", "fullscreen", "alwaysOnTop", "confirmClose", "fileDrop"}) {
            assertTrue((Boolean) set.get(key), key);
        }
        assertThrows(IllegalArgumentException.class, () -> WindowOptions.blank().minSize(0, 300));
        assertFalse(plain.containsKey("theme"), "the system's theme is the default");
        assertEquals("dark", WindowOptions.blank().theme(Theme.DARK).toPayload().get("theme"));
    }

    @Test
    void anAppOpensAnotherOfItsPages() {
        assertEquals(Assets.baseUrl() + "app/settings.html", WindowOptions.app("web").page("settings.html").toPayload().get("url"));
        assertThrows(IllegalStateException.class, () -> WindowOptions.url("https://example.com").page("x.html"));
        assertThrows(IllegalArgumentException.class, () -> WindowOptions.app("web").page("../secret.html"));
    }
}
