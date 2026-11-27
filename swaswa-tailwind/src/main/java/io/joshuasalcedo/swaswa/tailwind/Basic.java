package io.joshuasalcedo.swaswa.tailwind;

import io.joshuasalcedo.swaswa.css.CssClass;
import io.joshuasalcedo.swaswa.css.StyleSheet;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * swaswa's basic look, typed: a page, text, rows and stacks, cards, buttons, fields, badges, links. Each brings its own
 * CSS (Tailwind's, from {@code tailwind/basics.css}), so nothing else is needed:
 * <pre>{@code
 * Button save = new Button("Save");
 * save.cssClass(Basic.BTN_PRIMARY);
 * }</pre>
 * A Tailwind class on the same element wins over a basic one ({@code cssClass(Basic.CARD, Tw.P.P_8)}), as Tailwind's
 * utilities win over its components.
 */
public enum Basic implements CssClass {
    PAGE, CONTAINER,
    TITLE, SUBTITLE, MUTED, LINK,
    ROW, STACK, GRID_CARDS,
    CARD,
    BTN, BTN_PRIMARY, BTN_DANGER,
    INPUT,
    BADGE;

    @Override
    public String className() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    @Override
    public String css() {
        String rule = rules().get(className());
        if (rule == null) throw new IllegalStateException("tailwind/basics.css has no ." + className());
        return rule;
    }

    /** Before Tailwind's own classes: a utility on the same element wins. */
    @Override
    public long order() {
        return -1;
    }

    @Override
    public StyleSheet base() {
        return Tailwind.base();
    }

    private static volatile Map<String, String> rules;

    /** basics.css, its @apply done, by class: each class's whole rule. */
    private static Map<String, String> rules() {
        Map<String, String> r = rules;
        if (r == null) {
            String css;
            try (InputStream in = Basic.class.getClassLoader().getResourceAsStream("tailwind/basics.css")) {
                if (in == null) throw new IllegalStateException("tailwind/basics.css is not in the jar");
                css = new TailwindProcessor().process(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            r = new HashMap<>();
            int i = 0;
            while ((i = css.indexOf('.', i)) >= 0) {
                int open = css.indexOf('{', i);
                if (open < 0) break;
                String name = css.substring(i + 1, open).strip();
                int depth = 0, close = open;
                for (; close < css.length(); close++) {
                    if (css.charAt(close) == '{') depth++;
                    else if (css.charAt(close) == '}' && --depth == 0) break;
                }
                r.put(name, "." + name + " " + css.substring(open, close + 1));
                i = close + 1;
            }
            rules = r;
        }
        return r;
    }
}
