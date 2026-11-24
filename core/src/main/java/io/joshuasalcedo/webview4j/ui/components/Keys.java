package io.joshuasalcedo.webview4j.ui.components;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** How the components pass names and values between Java and the page. */
final class Keys {

    private Keys() {}

    static String camel(Enum<?> e) {
        String[] words = e.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder s = new StringBuilder(words[0]);
        for (int i = 1; i < words.length; i++) s.append(Character.toUpperCase(words[i].charAt(0))).append(words[i].substring(1));
        return s.toString();
    }

    static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    static boolean flag(Object value) {
        return Boolean.TRUE.equals(value);
    }

    /** A number the page sent; null for none. */
    static Double number(Object value) {
        return value instanceof Number n ? n.doubleValue() : null;
    }

    /** A whole number the page sent; null for none. */
    static Long whole(Object value) {
        return value instanceof Number n ? n.longValue() : null;
    }

    /** A list of texts the page sent. */
    static List<String> texts(Object value) {
        List<String> list = new ArrayList<>();
        if (value instanceof List<?> items) for (Object item : items) list.add(String.valueOf(item));
        return List.copyOf(list);
    }
}
