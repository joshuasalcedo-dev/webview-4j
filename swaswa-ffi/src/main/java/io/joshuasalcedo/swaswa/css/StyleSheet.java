package io.joshuasalcedo.swaswa.css;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * CSS rules for a page: by selector, in media queries, and for each colour scheme. Shown in a page by
 * a page's {@code Document.addStyleSheet}; what changes in it then (a rule added, a rule's style set) changes in the page.
 * <pre>{@code
 * StyleSheet sheet = new StyleSheet()
 *         .rule(":root", s -> s.set(CssProperty.COLOR_SCHEME, "light dark"))
 *         .rule(".card", s -> s.set(CssProperty.PADDING, "12px")
 *                 .set(CssProperty.BACKGROUND, Style.lightDark("#fff", "#25282f")))
 *         .dark(d -> d.rule(".logo", s -> s.set(CssProperty.FILTER, "invert(1)")))
 *         .media("(max-width: 600px)", m -> m.rule(".side", s -> s.set(CssProperty.DISPLAY, "none")));
 * }</pre>
 */
public final class StyleSheet {

    private sealed interface Item permits Rule, Block, Raw {}

    private record Rule(String selector, Style style) implements Item {}

    private record Block(String at, StyleSheet inside) implements Item {}

    private record Raw(String css) implements Item {}

    private final List<Item> items = new CopyOnWriteArrayList<>();
    /** Sheets this one needs in the page before it (a processor's base: Tailwind's variables). */
    private final List<StyleSheet> requires = new CopyOnWriteArrayList<>();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final Runnable changed = this::changed;

    public StyleSheet() {}

    // ---- from a file, as a page's <link rel="stylesheet"> ----

    /**
     * A CSS file on the class path ({@code "app.css"}, {@code "styles/app.css"}), as a page loads its style sheet: its
     * {@code @import}s followed (beside it first, then from the class path's root), then given to the CSS processors on
     * the class path (swaswa-tailwind's does {@code @apply}, {@code @theme}...). Read with the caller's class loader.
     *
     * @throws IllegalArgumentException if there is no such file
     */
    public static StyleSheet load(String resource) {
        Class<?> caller = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass();
        return load(resource, caller.getClassLoader());
    }

    /** {@link #load(String)} with {@code loader}. */
    public static StyleSheet load(String resource, ClassLoader loader) {
        java.util.Objects.requireNonNull(resource, "resource");
        String path = resource.startsWith("/") ? resource.substring(1) : resource;
        return parse(read(path, loader, new java.util.HashSet<>()), loader);
    }

    /** CSS as its author wrote it (its {@code @import}s from the class path), given to the CSS processors on the class path. */
    public static StyleSheet parse(String css, ClassLoader loader) {
        String text = imports(java.util.Objects.requireNonNull(css, "css"), "", loader, new java.util.HashSet<>());
        StyleSheet sheet = new StyleSheet();
        for (CssProcessor p : java.util.ServiceLoader.load(CssProcessor.class, loader)) {
            if (!p.handles(text)) continue;
            text = p.process(text);
            if (p.base() != null) sheet.require(p.base());
        }
        return sheet.css(text);
    }

    private static String read(String path, ClassLoader loader, java.util.Set<String> seen) {
        if (!seen.add(path)) return "";   // imported already
        try (java.io.InputStream in = loader.getResourceAsStream(path)) {
            if (in == null) throw new IllegalArgumentException("no style sheet " + path + " on the class path");
            String text = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            int slash = path.lastIndexOf('/');
            return imports(text, slash < 0 ? "" : path.substring(0, slash + 1), loader, seen);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private static final java.util.regex.Pattern IMPORT = java.util.regex.Pattern.compile(
            "@import\\s+(?:url\\(\\s*)?[\"']([^\"']+)[\"']\\s*\\)?\\s*;");

    /** Each {@code @import "file.css";} put in its place: beside the importing file first, then from the root. */
    private static String imports(String css, String dir, ClassLoader loader, java.util.Set<String> seen) {
        // comments are not CSS: an @import written in one is not followed (they are left out)
        java.util.regex.Matcher m = IMPORT.matcher(css.replaceAll("(?s)/\\*.*?\\*/", ""));
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String target = m.group(1);
            if (target.contains("://")) continue;   // a web address: left for the page
            String path = found(dir + target, loader);
            if (path == null) path = found(target.startsWith("/") ? target.substring(1) : target, loader);
            if (path == null) throw new IllegalArgumentException("@import \"" + target + "\": no such style sheet on the class path");
            m.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(read(path, loader, seen)));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** The file a path names, as it is or with .css after it ({@code @import "tailwindcss";}); null if neither is there. */
    private static String found(String path, ClassLoader loader) {
        if (loader.getResource(path) != null) return path;
        if (!path.endsWith(".css") && loader.getResource(path + ".css") != null) return path + ".css";
        return null;
    }

    /** A sheet this one needs in the page before it: a page it is added to gets that one first. */
    public StyleSheet require(StyleSheet sheet) {
        if (!requires.contains(java.util.Objects.requireNonNull(sheet, "sheet"))) requires.add(sheet);
        return this;
    }

    /** The sheets it needs in the page before it. */
    public List<StyleSheet> requires() {
        return List.copyOf(requires);
    }

    /** A rule: what {@code selector} matches has {@code style}; the style stays live (set more, the page has it). */
    public StyleSheet rule(String selector, Style style) {
        Objects.requireNonNull(style, "style");
        items.add(new Rule(selector(selector), style));
        style.listen(changed);
        changed();
        return this;
    }

    /** A rule, its style made here: {@code rule(".card", s -> s.set(CssProperty.PADDING, "12px"))}. */
    public StyleSheet rule(String selector, Consumer<Style> style) {
        Style s = new Style();
        style.accept(s);
        return rule(selector, s);
    }

    /** Rules for when {@code query} holds: {@code media("(max-width: 600px)", m -> ...)}. */
    public StyleSheet media(String query, Consumer<StyleSheet> rules) {
        return block("@media " + query(query), rules);
    }

    /** Rules for the dark scheme only ({@code @media (prefers-color-scheme: dark)}): a page's scheme follows its window's theme. */
    public StyleSheet dark(Consumer<StyleSheet> rules) {
        return block("@media (prefers-color-scheme: dark)", rules);
    }

    /** Rules for the light scheme only. */
    public StyleSheet light(Consumer<StyleSheet> rules) {
        return block("@media (prefers-color-scheme: light)", rules);
    }

    /** Rules for where {@code condition} holds: {@code supports("display: grid", s -> ...)}. */
    public StyleSheet supports(String condition, Consumer<StyleSheet> rules) {
        return block("@supports (" + query(condition) + ")", rules);
    }

    /** CSS as written ({@code @keyframes}, {@code @font-face}...): it cannot end the style element. */
    public StyleSheet css(String css) {
        Objects.requireNonNull(css, "css");
        if (css.toLowerCase(Locale.ROOT).contains("</style")) throw new IllegalArgumentException("CSS cannot hold </style");
        items.add(new Raw(css));
        changed();
        return this;
    }

    private StyleSheet block(String at, Consumer<StyleSheet> rules) {
        StyleSheet inside = new StyleSheet();
        rules.accept(inside);
        items.add(new Block(at, inside));
        inside.listeners.add(changed);
        changed();
        return this;
    }

    /** Takes its rules away. */
    public StyleSheet clear() {
        for (Item i : items) {
            if (i instanceof Rule r) r.style().unlisten(changed);
        }
        items.clear();
        changed();
        return this;
    }

    /** As CSS writes it. */
    public String toCss() {
        StringBuilder b = new StringBuilder();
        write(b, "");
        return b.toString();
    }

    private void write(StringBuilder b, String indent) {
        for (Item i : items) {
            switch (i) {
                case Rule r -> b.append(indent).append(r.selector()).append(" { ").append(r.style().toCss()).append(" }\n");
                case Block k -> {
                    b.append(indent).append(k.at()).append(" {\n");
                    k.inside().write(b, indent + "  ");
                    b.append(indent).append("}\n");
                }
                case Raw w -> b.append(w.css()).append('\n');
            }
        }
    }

    @Override
    public String toString() {
        return toCss();
    }

    private static String selector(String selector) {
        Objects.requireNonNull(selector, "selector");
        if (selector.isBlank() || selector.indexOf('{') >= 0 || selector.indexOf('}') >= 0 || selector.indexOf(';') >= 0
                || selector.contains("/*") || selector.toLowerCase(Locale.ROOT).contains("</style")) {
            throw new IllegalArgumentException("not a selector: " + selector);
        }
        return selector.strip();
    }

    private static String query(String query) {
        return selector(query);
    }

    // ---- who shows it ----

    void listen(Runnable listener) {
        listeners.add(listener);
    }

    void unlisten(Runnable listener) {
        listeners.remove(listener);
    }

    private void changed() {
        for (Runnable r : new ArrayList<>(listeners)) r.run();
    }
}
