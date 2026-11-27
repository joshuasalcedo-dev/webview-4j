package io.joshuasalcedo.swaswa.tailwind;

import io.joshuasalcedo.swaswa.css.CssClass;
import io.joshuasalcedo.swaswa.css.StyleSheet;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One Tailwind class, typed ({@link Tw}'s constants are these): its name, its CSS (Tailwind's own), and its variants.
 * Used on a component ({@code cssClass(...)}), its rule goes in the component's page, once.
 * <pre>{@code
 * Utility pad = Tw.P.P_4;                    // "p-4"
 * Utility hoverBlue = Tw.hover(Tw.Bg.BG_BLUE_500);   // "hover:bg-blue-500"
 * Utility wide = Tw.md(Tw.hover(Tw.P.P_8));  // "md:hover:p-8"
 * }</pre>
 */
public final class Utility implements CssClass {

    private final String base;
    /** Outermost first, as a class's name writes them: {@code md:hover:p-8} is md, hover. */
    private final List<Variant> variants;
    private final String name;

    /** A class Tailwind has, by its name without variants; checked when its CSS is first asked for. */
    Utility(String base) {
        this(base, List.of());
    }

    private Utility(String base, List<Variant> variants) {
        this.base = Objects.requireNonNull(base, "base");
        this.variants = List.copyOf(variants);
        StringBuilder n = new StringBuilder();
        for (Variant v : this.variants) n.append(v.prefix()).append(':');
        this.name = n.append(base).toString();
    }

    /**
     * A class Tailwind has, by name: {@code "p-4"}, {@code "bg-red-500"}, with Tailwind's variants too
     * ({@code "md:hover:p-8"}).
     *
     * @throws IllegalArgumentException if Tailwind has no such class, or no such variant
     */
    public static Utility of(String name) {
        Objects.requireNonNull(name, "name");
        String[] parts = name.split(":", -1);
        List<Variant> variants = new ArrayList<>();
        for (int i = 0; i < parts.length - 1; i++) variants.add(Variant.of(parts[i]));
        String base = parts[parts.length - 1];
        if (!Tailwind.has(base)) throw new IllegalArgumentException("Tailwind " + Tailwind.version() + " has no class \"" + base + "\"");
        return new Utility(base, variants);
    }

    /** This class, when {@code variant} says ({@code hover:} this): the variant outside those it has. */
    public Utility when(Variant variant) {
        List<Variant> all = new ArrayList<>();
        all.add(Objects.requireNonNull(variant, "variant"));
        all.addAll(variants);
        return new Utility(base, all);
    }

    @Override
    public String className() {
        return name;
    }

    /** Its name without variants: {@code "p-4"}. */
    public String baseName() {
        return base;
    }

    public List<Variant> variants() {
        return variants;
    }

    /** Its rule, as Tailwind writes it, its variants nested (CSS nesting, as Tailwind 4's output). */
    @Override
    public String css() {
        Tailwind.Entry e = Tailwind.entry(base);
        String selector = "." + Tailwind.escape(name);
        if (e.body() != null) {
            String body = e.body();
            for (int i = variants.size() - 1; i >= 0; i--) body = variants.get(i).nest(body);
            return selector + " { " + body + " }";
        }
        // a rule with the class elsewhere in its selector (space-x-4: its children): the variants' selectors around
        // the class, their at-rules around the rule
        String inner = selector;
        for (int i = variants.size() - 1; i >= 0; i--) inner = variants.get(i).select(inner);
        String rule = e.rule().replace("@@", inner);
        for (int i = variants.size() - 1; i >= 0; i--) rule = variants.get(i).wrap(rule);
        return rule;
    }

    /**
     * As Tailwind orders its CSS: a class with no variants first, then by its variants, the outermost deciding
     * (states, then breakpoints small to large, then dark, then print...), so {@code md:p-8} wins over {@code p-4}.
     */
    @Override
    public long order() {
        long order = 0;
        for (Variant v : variants) order = order * 64 + Variant.rank(v) + 1;
        return order;
    }

    /** Tailwind's theme variables, keyframes and defaults: once in a page that uses any of its classes. */
    @Override
    public StyleSheet base() {
        return Tailwind.base();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Utility u && u.name.equals(name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return name;
    }
}
