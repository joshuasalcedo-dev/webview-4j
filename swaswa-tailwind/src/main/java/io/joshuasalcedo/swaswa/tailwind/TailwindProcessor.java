package io.joshuasalcedo.swaswa.tailwind;

import io.joshuasalcedo.swaswa.css.CssProcessor;
import io.joshuasalcedo.swaswa.css.StyleSheet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tailwind's CSS, done in Java when a style sheet is loaded ({@link StyleSheet#load}), as Tailwind's build does it:
 * <ul>
 *   <li>{@code @apply p-4 rounded-lg hover:bg-indigo-500 dark:bg-slate-900;}: the classes' CSS put in its place;</li>
 *   <li>{@code @theme { --color-brand: #4f46e5; }}: the app's design tokens, as variables, and the classes they make
 *       ({@code --color-brand}: {@code bg-brand}, {@code text-brand}, {@code border-brand}...);</li>
 *   <li>{@code @utility name { ... }}: a class of the app's own, to {@code @apply}.</li>
 * </ul>
 */
public final class TailwindProcessor implements CssProcessor {

    @Override
    public boolean handles(String css) {
        return css.contains("@apply") || css.contains("@theme") || css.contains("@utility");
    }

    @Override
    public StyleSheet base() {
        return Tailwind.base();
    }

    @Override
    public String process(String css) {
        Map<String, String> tokens = new LinkedHashMap<>();
        Map<String, String> utilities = new LinkedHashMap<>();
        String text = blocks(withoutComments(css), tokens, utilities);
        return applies(text, tokens, utilities);
    }

    // ---- @theme and @utility: taken out, remembered ----

    private static final Pattern TOKEN = Pattern.compile("(--[A-Za-z0-9_-]+)\\s*:\\s*([^;]+);");

    private static String blocks(String css, Map<String, String> tokens, Map<String, String> utilities) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < css.length()) {
            boolean theme = css.startsWith("@theme", i), utility = css.startsWith("@utility", i);
            if (!theme && !utility) {
                out.append(css.charAt(i++));
                continue;
            }
            int open = css.indexOf('{', i);
            if (open < 0) throw new IllegalArgumentException("an @theme or @utility with no { }");
            String name = css.substring(i + (theme ? 6 : 8), open).strip();
            int close = matching(css, open);
            String body = css.substring(open + 1, close).strip();
            if (theme) {
                Matcher m = TOKEN.matcher(body);
                while (m.find()) tokens.put(m.group(1), m.group(2).strip());
                out.append(":root, :host { ").append(body).append(" }");   // its variables, as Tailwind writes them
            } else {
                if (!name.matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException("@utility " + name + ": a class's name, one word");
                utilities.put(name, body);
            }
            i = close + 1;
        }
        return out.toString();
    }

    // ---- @apply: the classes' CSS in its place ----

    private static final Pattern APPLY = Pattern.compile("@apply\\s+([^;}]+);?");

    private static String applies(String css, Map<String, String> tokens, Map<String, String> utilities) {
        Matcher m = APPLY.matcher(css);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            List<String> parts = new ArrayList<>();
            for (String c : m.group(1).trim().split("\\s+")) parts.add(applied(c, tokens, utilities));
            m.appendReplacement(out, Matcher.quoteReplacement(String.join(" ", parts)));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** One class, with its variants, as declarations (its variants' as nested rules). */
    private static String applied(String name, Map<String, String> tokens, Map<String, String> utilities) {
        String[] parts = name.split(":", -1);
        String base = parts[parts.length - 1];
        String body = utilities.get(base);
        if (body == null) body = bodyOf(base, tokens);
        for (int i = parts.length - 2; i >= 0; i--) body = Variant.of(parts[i]).nest(body);
        return body;
    }

    private static String bodyOf(String base, Map<String, String> tokens) {
        if (Tailwind.has(base)) {
            Tailwind.Entry e = Tailwind.entry(base);
            if (e.body() == null) {
                throw new IllegalArgumentException("@apply " + base + ": it styles an element's children, so it goes on the element itself");
            }
            return e.body();
        }
        // a class the app's tokens make: bg-brand, from --color-brand (as bg-red-50 is from --color-red-50)
        for (Map.Entry<String, String> token : tokens.entrySet()) {
            String derived = derived(base, token.getKey());
            if (derived != null) return derived;
        }
        throw new IllegalArgumentException("@apply " + base + ": Tailwind " + Tailwind.version() + " has no such class, nor do the @theme's tokens make it");
    }

    /** {@code bg-brand} from {@code --color-brand}: a class of the same family, made with one of Tailwind's own tokens. */
    private static String derived(String base, String token) {
        int dash = token.indexOf('-', 2);
        if (dash < 0) return null;
        String family = token.substring(0, dash + 1);   // --color-
        String ours = token.substring(dash + 1);         // brand
        if (!base.endsWith("-" + ours) && !base.contains("-" + ours + "-")) return null;
        String theirs = Tailwind.firstToken(family);      // --color-red-50
        if (theirs == null) return null;
        String like = base.replace(ours, theirs.substring(family.length()));   // bg-red-50
        if (!Tailwind.has(like)) return null;
        String body = Tailwind.entry(like).body();
        if (body == null || !body.contains("var(" + theirs)) return null;
        return body.replace("var(" + theirs + ")", "var(" + token + ")").replace("var(" + theirs + "--", "var(" + token + "--");
    }

    // ---- small things ----

    private static String withoutComments(String css) {
        return css.replaceAll("(?s)/\\*.*?\\*/", "");
    }

    private static int matching(String css, int open) {
        int depth = 0;
        for (int i = open; i < css.length(); i++) {
            if (css.charAt(i) == '{') depth++;
            else if (css.charAt(i) == '}' && --depth == 0) return i;
        }
        throw new IllegalArgumentException("a { with no }");
    }
}
