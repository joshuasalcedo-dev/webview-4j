package io.joshuasalcedo.webview4j.sampler.pages;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One category's pages of the gallery. Its fields are what its demos change (a text typed, a row chosen), so the
 * demos work: each page's demo reads them when the gallery draws it again.
 */
public interface Demos {

    void addTo(Gallery gallery);

    /** Pairs in the order given (Map.of has none). */
    static Map<String, String> ordered(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) map.put(pairs[i], pairs[i + 1]);
        return map;
    }
}
