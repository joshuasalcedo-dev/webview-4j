package io.joshuasalcedo.webview4j;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JsonTest {

    @Test
    void readsEveryKindOfValue() {
        Map<String, Object> json = Json.object(
                " {\"s\":\"a\\\"b\\\\c\\n\\u00e9\",\"i\":-42,\"d\":1.5e2,\"t\":true,\"f\":false,\"n\":null,\"a\":[1,[],{}]} ");
        assertEquals("a\"b\\c\né", json.get("s"));
        assertEquals(-42L, json.get("i"));
        assertEquals(150.0, json.get("d"));
        assertEquals(true, json.get("t"));
        assertEquals(false, json.get("f"));
        assertTrue(json.containsKey("n"));
        assertNull(json.get("n"));
        assertEquals(List.of(1L, List.of(), Map.of()), json.get("a"));
        assertEquals(List.of("s", "i", "d", "t", "f", "n", "a"), List.copyOf(json.keySet()), "keys keep their order");
    }

    @Test
    void writesWhatItReads() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("text", "line\nquote\" tab\t \u2028");
        value.put("list", Arrays.asList(1, 2.5, null, true));
        value.put("nested", Map.of("k", "v"));
        String written = Json.write(value);
        assertEquals("{\"text\":\"line\\nquote\\\" tab\\t \\u2028\",\"list\":[1,2.5,null,true],\"nested\":{\"k\":\"v\"}}", written);
        assertEquals(value.get("text"), Json.object(written).get("text"));
    }

    @Test
    void refusesWhatIsNotJson() {
        for (String bad : new String[] {"", "{", "{\"a\"}", "[1,]", "tru", "\"open", "{} extra", "nan"}) {
            assertThrows(IllegalArgumentException.class, () -> Json.parse(bad), bad);
        }
        assertThrows(IllegalArgumentException.class, () -> Json.write(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> Json.object("[1]"));
    }
}
