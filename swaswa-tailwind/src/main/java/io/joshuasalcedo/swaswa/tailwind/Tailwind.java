package io.joshuasalcedo.swaswa.tailwind;

import io.joshuasalcedo.swaswa.css.StyleSheet;
import io.joshuasalcedo.swaswa.internal.Json;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * Tailwind, in Java: every utility class Tailwind has (its default theme), with its CSS, read from Tailwind itself when
 * this library was built (no Node, no Tailwind build for an app). A page gets the CSS of the classes its components
 * use, and only those ({@link Tw}, {@link Utility}); the theme's variables come with the first ({@link #base()}).
 * Tailwind's reset is the app's to add, if it wants it ({@link #preflight()}).
 */
public final class Tailwind {

    private Tailwind() {}

    /** One class: the body of its {@code .name { }} rule, or a rule with its class written {@code @@}. */
    record Entry(String body, String rule) {}

    private static volatile Data data;

    private record Data(String version, Map<String, Entry> classes, StyleSheet base, StyleSheet preflight, String baseCss) {}

    private static Data data() {
        Data d = data;
        if (d == null) {
            synchronized (Tailwind.class) {
                d = data;
                if (d == null) data = d = load();
            }
        }
        return d;
    }

    @SuppressWarnings("unchecked")
    private static Data load() {
        try (InputStream in = Tailwind.class.getResourceAsStream("tailwind.json.gz")) {
            if (in == null) throw new IllegalStateException("tailwind.json.gz is not in the jar");
            Map<String, Object> json = Json.object(new String(new GZIPInputStream(in).readAllBytes(), StandardCharsets.UTF_8));
            Map<String, Entry> classes = new java.util.LinkedHashMap<>();   // in Tailwind's own order
            for (Object o : (List<Object>) json.get("classes")) {
                Map<String, Object> c = (Map<String, Object>) o;
                classes.put((String) c.get("n"), new Entry((String) c.get("b"), (String) c.get("r")));
            }
            String base = (String) json.get("base");
            return new Data((String) json.get("version"), Collections.unmodifiableMap(classes),
                    new StyleSheet().css(base), new StyleSheet().css((String) json.get("preflight")), base);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The version of Tailwind its classes are from. */
    public static String version() {
        return data().version();
    }

    /** Whether Tailwind has the class {@code name} (without variants: {@code "p-4"}, not {@code "hover:p-4"}). */
    public static boolean has(String name) {
        return data().classes().containsKey(name);
    }

    /** Every class Tailwind has, by name (without variants), in Tailwind's own order (red-50, red-100... red-950). */
    public static Set<String> names() {
        return data().classes().keySet();
    }

    /** What every page using Tailwind's classes needs once: the theme's variables, its keyframes, its @property defaults. */
    public static StyleSheet base() {
        return data().base();
    }

    /** Tailwind's reset (preflight): margins off, box-sizing border-box, plain lists and buttons... the app adds it if it wants. */
    public static StyleSheet preflight() {
        return data().preflight();
    }

    /** The first of Tailwind's theme tokens in a family ({@code --color-}: {@code --color-red-50}); null if none. */
    static String firstToken(String family) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(" + java.util.regex.Pattern.quote(family) + "[A-Za-z0-9_-]+)\s*:")
                .matcher(data().baseCss());
        while (m.find()) {
            if (!m.group(1).substring(family.length()).contains("--")) return m.group(1);   // not --text-xs--line-height
        }
        return null;
    }

    static Entry entry(String name) {
        Entry e = data().classes().get(name);
        if (e == null) throw new IllegalArgumentException("Tailwind " + version() + " has no class \"" + name + "\"");
        return e;
    }

    /** A class name as a CSS selector writes it: what is not a letter, digit, - or _ escaped. */
    static String escape(String name) {
        StringBuilder b = new StringBuilder(name.length() + 8);
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean plain = Character.isLetterOrDigit(c) && c < 128 || c == '-' || c == '_';
            boolean leadingDigit = Character.isDigit(c) && (i == 0 || i == 1 && name.charAt(0) == '-');
            if (leadingDigit) b.append("\\3").append(c).append(' ');
            else if (plain) b.append(c);
            else b.append('\\').append(c);
        }
        return b.toString();
    }
}
