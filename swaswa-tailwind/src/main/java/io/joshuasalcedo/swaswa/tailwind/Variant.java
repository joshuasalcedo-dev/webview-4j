package io.joshuasalcedo.swaswa.tailwind;

import java.util.Objects;

/**
 * A Tailwind variant: when a class applies ({@code hover:}, {@code md:}, {@code dark:}...), as Tailwind 4 writes it.
 * One that is a state of the element itself nests a selector ({@code &:hover}); one about the page or a parent nests an
 * at-rule ({@code @media (width >= 48rem)}) or a selector around the element ({@code &:is(:where(.group):hover *)}).
 *
 * @param prefix its word in a class's name ({@code "hover"})
 * @param kind   what it nests
 * @param text   the selector (with {@code &} for the element) or the at-rule
 * @param within an at-rule inside its selector as well ({@code hover}: {@code @media (hover: hover)}); null for none
 */
public record Variant(String prefix, Kind kind, String text, String within) {

    public enum Kind {
        /** A selector with {@code &}: {@code &:hover}, {@code &:is(:where(.group):hover *)}. */
        SELECTOR,
        /** An at-rule: {@code @media (width >= 48rem)}. */
        AT_RULE
    }

    public Variant {
        Objects.requireNonNull(prefix, "prefix");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(text, "text");
        if (prefix.isBlank() || prefix.contains(" ") || prefix.contains(":")) throw new IllegalArgumentException("a variant's prefix is one word: " + prefix);
        if (kind == Kind.SELECTOR && !text.contains("&")) throw new IllegalArgumentException("a variant's selector says where the element is: &");
    }

    /** One for a state of the element, or around it: {@code selector("aria-busy", "&[aria-busy=true]")}. */
    public static Variant selector(String prefix, String selector) {
        return new Variant(prefix, Kind.SELECTOR, selector, null);
    }

    /** One for a media query: {@code media("tall", "(height >= 60rem)")}. */
    public static Variant media(String prefix, String query) {
        return new Variant(prefix, Kind.AT_RULE, "@media " + query, null);
    }

    /**
     * Tailwind's variant written {@code prefix} ({@code "hover"}, {@code "md"}, {@code "dark"}...).
     *
     * @throws IllegalArgumentException if it is not one of Tailwind's this library has
     */
    public static Variant of(String prefix) {
        for (java.lang.reflect.Field f : Variant.class.getFields()) {
            if (f.getType() != Variant.class || !java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
            try {
                Variant v = (Variant) f.get(null);
                if (v.prefix().equals(prefix)) return v;
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalArgumentException("no Tailwind variant \"" + prefix + "\" here: make one with Variant.selector(...) or Variant.media(...)");
    }

    /** Its place in Tailwind's order of variants (as Tailwind 4 registers them); one of the app's own after them. */
    static int rank(Variant v) {
        int i = ORDER.indexOf(v);
        return i < 0 ? ORDER.size() : i;
    }

    /** Its rule's body nested in it: {@code &:hover { body }}. */
    String nest(String body) {
        String inner = within == null ? body : within + " { " + body + " }";
        return text + " { " + inner + " }";
    }

    /** A selector (of a rule a class is written in) for when it applies. */
    String select(String selector) {
        return kind == Kind.SELECTOR ? text.replace("&", selector) : selector;
    }

    /** A whole rule, for when it applies: an at-rule around it. */
    String wrap(String rule) {
        String r = within == null ? rule : within + " { " + rule + " }";
        return kind == Kind.AT_RULE ? text + " { " + r + " }" : r;
    }

    // ---- Tailwind 4's own ----

    /** Only where a pointer can hover (as Tailwind 4: not a touch screen's tap). */
    public static final Variant HOVER = new Variant("hover", Kind.SELECTOR, "&:hover", "@media (hover: hover)");
    public static final Variant FOCUS = selector("focus", "&:focus");
    public static final Variant FOCUS_VISIBLE = selector("focus-visible", "&:focus-visible");
    public static final Variant FOCUS_WITHIN = selector("focus-within", "&:focus-within");
    public static final Variant ACTIVE = selector("active", "&:active");
    public static final Variant VISITED = selector("visited", "&:visited");
    public static final Variant DISABLED = selector("disabled", "&:disabled");
    public static final Variant ENABLED = selector("enabled", "&:enabled");
    public static final Variant CHECKED = selector("checked", "&:checked");
    public static final Variant REQUIRED = selector("required", "&:required");
    public static final Variant INVALID = selector("invalid", "&:invalid");
    public static final Variant PLACEHOLDER_SHOWN = selector("placeholder-shown", "&:placeholder-shown");
    public static final Variant OPEN = selector("open", "&:is([open], :popover-open, :open)");
    public static final Variant FIRST = selector("first", "&:first-child");
    public static final Variant LAST = selector("last", "&:last-child");
    public static final Variant ONLY = selector("only", "&:only-child");
    public static final Variant ODD = selector("odd", "&:nth-child(odd)");
    public static final Variant EVEN = selector("even", "&:nth-child(even)");
    public static final Variant EMPTY = selector("empty", "&:empty");
    public static final Variant PLACEHOLDER = selector("placeholder", "&::placeholder");
    public static final Variant SELECTION = selector("selection", "& *::selection, &::selection");
    public static final Variant MARKER = selector("marker", "& *::marker, &::marker");
    public static final Variant FILE = selector("file", "&::file-selector-button");
    /** A {@code group} parent hovered ({@link Tw#GROUP} on it). */
    public static final Variant GROUP_HOVER = new Variant("group-hover", Kind.SELECTOR, "&:is(:where(.group):hover *)", "@media (hover: hover)");
    public static final Variant GROUP_FOCUS = selector("group-focus", "&:is(:where(.group):focus *)");
    /** A {@code peer} sibling before it hovered ({@link Tw#PEER} on it). */
    public static final Variant PEER_HOVER = new Variant("peer-hover", Kind.SELECTOR, "&:is(:where(.peer):hover ~ *)", "@media (hover: hover)");
    public static final Variant PEER_CHECKED = selector("peer-checked", "&:is(:where(.peer):checked ~ *)");
    /** The page in its dark scheme: as its window's theme (or the system's) says. */
    public static final Variant DARK = media("dark", "(prefers-color-scheme: dark)");
    public static final Variant SM = media("sm", "(width >= 40rem)");
    public static final Variant MD = media("md", "(width >= 48rem)");
    public static final Variant LG = media("lg", "(width >= 64rem)");
    public static final Variant XL = media("xl", "(width >= 80rem)");
    public static final Variant XXL = media("2xl", "(width >= 96rem)");
    public static final Variant MAX_SM = media("max-sm", "(width < 40rem)");
    public static final Variant MAX_MD = media("max-md", "(width < 48rem)");
    public static final Variant MAX_LG = media("max-lg", "(width < 64rem)");
    public static final Variant PRINT = media("print", "print");
    public static final Variant MOTION_SAFE = media("motion-safe", "(prefers-reduced-motion: no-preference)");
    public static final Variant MOTION_REDUCE = media("motion-reduce", "(prefers-reduced-motion: reduce)");
    public static final Variant PORTRAIT = media("portrait", "(orientation: portrait)");
    public static final Variant LANDSCAPE = media("landscape", "(orientation: landscape)");

    /** Tailwind 4's order: states of the element, of a group and a peer, then breakpoints (max first), dark, print... */
    private static final java.util.List<Variant> ORDER = java.util.List.of(
            FIRST, LAST, ONLY, ODD, EVEN, EMPTY, PLACEHOLDER, FILE, MARKER, SELECTION,
            GROUP_HOVER, GROUP_FOCUS, PEER_HOVER, PEER_CHECKED,
            OPEN, CHECKED, REQUIRED, INVALID, PLACEHOLDER_SHOWN, ENABLED, DISABLED, VISITED,
            FOCUS_WITHIN, HOVER, FOCUS, FOCUS_VISIBLE, ACTIVE,
            MOTION_SAFE, MOTION_REDUCE,
            MAX_LG, MAX_MD, MAX_SM, SM, MD, LG, XL, XXL,
            PORTRAIT, LANDSCAPE, DARK, PRINT);
}
