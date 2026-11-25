package io.joshuasalcedo.webview4j.fx;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** What a person types in an address bar, as an address: a bare name gets https://, words become a search. */
final class Addresses {

    private Addresses() {}

    static String of(String text) {
        String url = text == null ? "" : text.strip();
        if (url.isEmpty()) return null;
        if (url.matches("(?i)^[a-z][a-z0-9+.-]*:.*") && !url.matches("(?i)^localhost:[0-9]+.*")) return url;
        if (!url.contains(" ") && (url.contains(".") || url.startsWith("localhost"))) {
            return (url.startsWith("localhost") ? "http://" : "https://") + url;
        }
        return "https://duckduckgo.com/?q=" + URLEncoder.encode(url, StandardCharsets.UTF_8);
    }
}
