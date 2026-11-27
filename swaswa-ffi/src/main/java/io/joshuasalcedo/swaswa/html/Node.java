package io.joshuasalcedo.swaswa.html;

import java.util.Map;

/**
 * Something in a page: an element ({@link HtmlElement}) or text ({@link Text}). Made in Java, it is either written out
 * as HTML ({@link #toHtml()}), or shown live in a view ({@link Document}): then what changes in Java changes in the page.
 */
public abstract class Node {

    HtmlElement<?> parent;
    /** Its number in the page it is shown in; 0 while it is in none. */
    int key;
    Document document;

    Node() {}

    /** The element it is in; null if none. */
    public HtmlElement<?> getParent() {
        return parent;
    }

    /** Whether it is shown in a page now. */
    public boolean isShowing() {
        return document != null;
    }

    /** It, as HTML. */
    public String toHtml() {
        StringBuilder out = new StringBuilder();
        write(out);
        return out.toString();
    }

    abstract void write(StringBuilder out);

    /** It, as the page builds it: numbered in {@code document}, its own and its children's. */
    abstract Map<String, Object> tree(Document document);

    /** Out of its page: its own and its children's numbers given back. */
    abstract void leave();

    /** The page's change, if it is shown in one. */
    final void op(Object... op) {
        Document d = document;
        if (d != null) d.op(op);
    }

    static String escape(String text) {
        StringBuilder b = new StringBuilder(text.length() + 8);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '&' -> b.append("&amp;");
                case '<' -> b.append("&lt;");
                case '>' -> b.append("&gt;");
                case '"' -> b.append("&quot;");
                default -> b.append(c);
            }
        }
        return b.toString();
    }
}
