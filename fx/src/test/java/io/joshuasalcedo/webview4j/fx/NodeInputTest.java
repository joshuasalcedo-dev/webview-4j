package io.joshuasalcedo.webview4j.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;

import javafx.scene.input.KeyCode;
import org.junit.jupiter.api.Test;

class NodeInputTest {

    @Test
    void keys_reach_the_page_as_windows_numbers_them() {
        assertEquals(13, NodeRendering.virtualKey(KeyCode.ENTER), "JavaFX says 10");
        assertEquals(46, NodeRendering.virtualKey(KeyCode.DELETE), "JavaFX says 127");
        assertEquals(190, NodeRendering.virtualKey(KeyCode.PERIOD));
        assertEquals(65, NodeRendering.virtualKey(KeyCode.A));
        assertEquals(37, NodeRendering.virtualKey(KeyCode.LEFT));
        assertEquals(8, NodeRendering.virtualKey(KeyCode.BACK_SPACE));
    }

    @Test
    void modifiers_are_the_protocols_bits() {
        assertEquals(0, NodeRendering.modifiers(false, false, false, false));
        assertEquals(1 | 8, NodeRendering.modifiers(true, false, false, true));
        assertEquals(2, NodeRendering.modifiers(false, true, false, false));
    }
}
