package io.joshuasalcedo.webview4j.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import javafx.geometry.BoundingBox;
import org.junit.jupiter.api.Test;

class FxPartsTest {

    @Test
    void a_docked_page_covers_the_node_in_physical_pixels() {
        var node = new BoundingBox(100, 50, 400, 300);
        var screen = new BoundingBox(0, 0, 1920, 1080);
        assertEquals(new Placement(150, 75, 600, 450), Placement.of(node, screen, 1.5, 1.5));
    }

    @Test
    void a_page_is_cut_to_what_shows_of_its_node() {
        var node = new BoundingBox(100, 50, 400, 300);
        var viewport = new BoundingBox(0, 200, 1000, 1000); // a scroll pane showing the node's lower half
        assertEquals(new Placement(100, 200, 400, 150), Placement.of(node, viewport, 1, 1));
        assertNull(Placement.of(node, new BoundingBox(600, 0, 100, 100), 1, 1), "nothing shows: no place");
        assertEquals(0, Placement.intersect(node, new BoundingBox(600, 0, 100, 100)).getWidth());
    }

    @Test
    void values_go_into_the_page_as_strings_never_as_code() {
        assertEquals("\"plain\"", Dom.js("plain"));
        assertEquals("\"\\\"); alert(1); (\\\"\"", Dom.js("\"); alert(1); (\""));
        assertEquals("\"\\u003c/script>\"", Dom.js("</script>"));
        assertEquals("\"a\\nb\\u2028c\\\\\"", Dom.js("a\nb\u2028c\\"));
        assertEquals("null", Dom.js(null));
    }

    @Test
    void each_change_picks_its_elements_with_the_selector_and_counts_them() {
        List<String> scripts = new ArrayList<>();
        Dom dom = new Dom(script -> {
            scripts.add(script);
            return CompletableFuture.completedFuture(3L);
        });
        assertEquals(3, dom.setText(".card", "x").join());
        assertTrue(scripts.getFirst().contains("document.querySelectorAll(\".card\")"));
        assertTrue(scripts.getFirst().contains("e.textContent = \"x\";"));
        assertFalse(scripts.getFirst().contains("'x'"));
    }

    @Test
    void sizes_read_as_people_say_them() {
        assertEquals("", Bytes.of(0));
        assertEquals("512 B", Bytes.of(512));
        assertEquals("1.5 KB", Bytes.of(1536).replace(',', '.'));
        assertEquals("2.0 MB", Bytes.of(2 * 1024 * 1024).replace(',', '.'));
    }
}
