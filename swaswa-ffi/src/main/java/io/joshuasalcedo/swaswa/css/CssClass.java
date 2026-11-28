package io.joshuasalcedo.swaswa.css;

/**
 * A CSS class, typed, that may bring its own CSS: a page it is used in gets its rule, once ({@link #css()}), and what
 * its rule needs once in a page ({@link #base()}: variables, keyframes). A library of classes (Tailwind's, in
 * swaswa-tailwind) is a set of these; a page then has the CSS of the classes it uses, and only those.
 * <pre>{@code
 * button.cssClass(Tw.P.P_4, Tw.hover(Tw.Bg.BG_BLUE_500));
 * }</pre>
 */
public interface CssClass {

    /** The class, as HTML writes it: one word, no spaces ({@code "hover:bg-blue-500"}). */
    String className();

    /** Its rule (or rules), as CSS, for a page it is used in; null when the page's own CSS has it. */
    default String css() {
        return null;
    }

    /**
     * Where its rule goes among the page's typed classes' rules, lowest first: a later rule wins over an earlier one of
     * the same weight, so a class meant to win (a variant's, {@code md:p-8} over {@code p-4}) sorts after. 0 unless said;
     * equal ones in the order they were first used.
     */
    default long order() {
        return 0;
    }

    /** What its CSS needs once in a page, shared by a library's classes (one sheet); null for nothing. */
    default StyleSheet base() {
        return null;
    }

    /** A class by name, with no CSS of its own: the page's style sheets have it. */
    static CssClass of(String name) {
        record Named(String className) implements CssClass {}
        if (name == null || name.isBlank() || name.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("one class, no spaces: \"" + name + "\"");
        }
        return new Named(name);
    }
}
