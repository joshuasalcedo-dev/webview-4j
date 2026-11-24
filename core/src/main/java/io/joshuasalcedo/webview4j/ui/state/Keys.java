package io.joshuasalcedo.webview4j.ui.state;

import java.util.List;
import java.util.StringJoiner;

/** State keys: dotted parts ({@code "orders.page.2"}), matched by patterns as the bridge's subjects are. */
final class Keys {

    private Keys() {}

    /** A key from its parts: {@code key("orders", query)} is {@code "orders.<query's text>"}. */
    static String of(Object... parts) {
        StringJoiner j = new StringJoiner(".");
        for (Object p : parts) j.add(String.valueOf(p).replace(' ', '_'));
        return j.toString();
    }

    /** {@code *} is one part, {@code >} one or more at the end; anything else is itself. */
    static boolean matches(String pattern, String key) {
        List<String> p = List.of(pattern.split("\\.", -1));
        List<String> k = List.of(key.split("\\.", -1));
        for (int i = 0; i < p.size(); i++) {
            String part = p.get(i);
            if (part.equals(">")) return i == p.size() - 1 && k.size() > i;
            if (i >= k.size()) return false;
            if (!part.equals("*") && !part.equals(k.get(i))) return false;
        }
        return p.size() == k.size();
    }
}
