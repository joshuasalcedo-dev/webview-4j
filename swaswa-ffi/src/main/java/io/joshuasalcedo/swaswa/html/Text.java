package io.joshuasalcedo.swaswa.html;

import java.util.Map;
import java.util.Objects;

/** Text in an element, beside other children: {@code Html.p(Html.text("Paid on "), Html.strong("Monday"))}. */
public final class Text extends Node {

    private String text;

    public Text(String text) {
        this.text = Objects.requireNonNull(text, "text");
    }

    public String getText() {
        return text;
    }

    /** Its text now: the page shows it. */
    public Text setText(String text) {
        this.text = Objects.requireNonNull(text, "text");
        op("text", key, text);
        return this;
    }

    @Override
    void write(StringBuilder out) {
        out.append(escape(text));
    }

    @Override
    Map<String, Object> tree(Document document) {
        this.document = document;
        key = document.number(this);
        return Map.of("key", key, "text", text);
    }

    @Override
    void leave() {
        if (document != null) document.forget(key);
        document = null;
        key = 0;
    }

    @Override
    public String toString() {
        return text;
    }
}
