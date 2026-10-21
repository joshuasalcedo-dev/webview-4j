package io.joshuasalcedo.webview4j;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WindowOptionsTest {

    @Test
    void ownerWindowGoesIntoThePayloadOnlyWhenSet() {
        assertFalse(WindowOptions.blank().toPayload().containsKey("ownerWindow"));
        assertEquals(0x1234L, WindowOptions.blank().ownerWindow(0x1234).toPayload().get("ownerWindow"));
        assertThrows(IllegalArgumentException.class, () -> WindowOptions.blank().ownerWindow(0));
    }
}
