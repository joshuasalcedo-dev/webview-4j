package io.joshuasalcedo.webview4j.fx;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * The page's elements, changed from Java: each call picks elements with a CSS selector and returns how many it
 * changed (or what it read). Values go into the page as JSON strings, never pasted into the script, so a quote or a
 * {@code </script>} in them is just text.
 *
 * <pre>{@code
 * web.dom().setText("h1", "Hello from JavaFX");
 * web.dom().setStyle("a", "outline", "2px solid #e11d48");
 * web.dom().append("body", "<p class='note'>Added by Java</p>");
 * web.dom().text("title").thenAccept(System.out::println);
 * }</pre>
 */
public final class Dom {

    private final Function<String, CompletableFuture<Object>> eval;

    Dom(Function<String, CompletableFuture<Object>> eval) {
        this.eval = eval;
    }

    /** How many elements match. */
    public CompletableFuture<Integer> count(String selector) {
        return each(selector, "");
    }

    /** The first match's text, or null when nothing matches. */
    public CompletableFuture<String> text(String selector) {
        return eval.apply("(() => { const e = document.querySelector(" + js(selector) + "); return e ? e.textContent : null; })()")
                .thenApply(value -> value == null ? null : value.toString());
    }

    /** The whole page's HTML, as it is now. */
    public CompletableFuture<String> html() {
        return eval.apply("document.documentElement.outerHTML").thenApply(String::valueOf);
    }

    public CompletableFuture<Integer> setText(String selector, String text) {
        return each(selector, "e.textContent = " + js(text) + ";");
    }

    /** Replaces the matches' contents with this HTML: the page's own, so only HTML you trust. */
    public CompletableFuture<Integer> setHtml(String selector, String html) {
        return each(selector, "e.innerHTML = " + js(html) + ";");
    }

    /** One CSS property, e.g. {@code setStyle("body", "background", "#111")}; an empty value removes it. */
    public CompletableFuture<Integer> setStyle(String selector, String property, String value) {
        return each(selector, "e.style.setProperty(" + js(property) + ", " + js(value) + ");");
    }

    public CompletableFuture<Integer> setAttribute(String selector, String name, String value) {
        return each(selector, "e.setAttribute(" + js(name) + ", " + js(value) + ");");
    }

    public CompletableFuture<Integer> addClass(String selector, String name) {
        return each(selector, "e.classList.add(" + js(name) + ");");
    }

    public CompletableFuture<Integer> removeClass(String selector, String name) {
        return each(selector, "e.classList.remove(" + js(name) + ");");
    }

    public CompletableFuture<Integer> toggleClass(String selector, String name) {
        return each(selector, "e.classList.toggle(" + js(name) + ");");
    }

    /** Adds this HTML at the end of each match. */
    public CompletableFuture<Integer> append(String selector, String html) {
        return each(selector, "e.insertAdjacentHTML('beforeend', " + js(html) + ");");
    }

    /** Adds this HTML at the start of each match. */
    public CompletableFuture<Integer> prepend(String selector, String html) {
        return each(selector, "e.insertAdjacentHTML('afterbegin', " + js(html) + ");");
    }

    public CompletableFuture<Integer> remove(String selector) {
        return each(selector, "e.remove();");
    }

    /** Clicks each match, as the user would. */
    public CompletableFuture<Integer> click(String selector) {
        return each(selector, "e.click();");
    }

    /** Types into the matching fields: sets their value and tells the page ({@code input} and {@code change}). */
    public CompletableFuture<Integer> setValue(String selector, String value) {
        return each(selector, "e.value = " + js(value) + "; e.dispatchEvent(new Event('input', {bubbles: true}));"
                + " e.dispatchEvent(new Event('change', {bubbles: true}));");
    }

    /** Scrolls the first match into view. */
    public CompletableFuture<Integer> scrollTo(String selector) {
        return eval.apply("(() => { const e = document.querySelector(" + js(selector) + ");"
                + " if (e) e.scrollIntoView({behavior: 'smooth', block: 'center'}); return e ? 1 : 0; })()").thenApply(Dom::number);
    }

    /** Outlines the matches for a moment, to show which they are. */
    public CompletableFuture<Integer> flash(String selector) {
        return each(selector, "const before = e.style.outline; e.style.outline = '3px solid #f43f5e';"
                + " setTimeout(() => { e.style.outline = before; }, 1200);");
    }

    /** Runs {@code body} for each match, with the element as {@code e}; returns how many there were. */
    private CompletableFuture<Integer> each(String selector, String body) {
        Objects.requireNonNull(selector, "selector");
        return eval.apply("(() => { const all = document.querySelectorAll(" + js(selector) + ");"
                + " for (const e of all) { " + body + " } return all.length; })()").thenApply(Dom::number);
    }

    private static int number(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }

    /** {@code value} as a JavaScript string literal (JSON, with the two line separators JavaScript breaks on). */
    static String js(String value) {
        if (value == null) return "null";
        StringBuilder out = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '<' -> out.append("\\u003c");
                default -> {
                    if (c < 0x20 || c == ' ' || c == ' ') out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.append('"').toString();
    }
}
