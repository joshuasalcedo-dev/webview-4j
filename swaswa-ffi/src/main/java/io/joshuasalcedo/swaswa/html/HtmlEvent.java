package io.joshuasalcedo.swaswa.html;

import io.joshuasalcedo.swaswa.component.Event;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** An element's event, from its page ({@link Event}): what came with it, typed; for a drop, the element dragged there. */
public final class HtmlEvent implements Event {

    private final HtmlElement<?> source;
    private final String type;
    private final Map<String, Object> data;
    private final HtmlElement<?> dragged;

    HtmlEvent(HtmlElement<?> source, String type, Map<String, Object> data, HtmlElement<?> dragged) {
        this.source = Objects.requireNonNull(source, "source");
        this.type = Objects.requireNonNull(type, "type");
        this.data = new LinkedHashMap<>(data);
        this.dragged = dragged;
    }

    @Override public HtmlElement<?> source() { return source; }

    @Override public String type() { return type; }

    @Override public HtmlElement<?> dragged() { return dragged; }

    @Override public String value() { return text("value"); }

    @Override public boolean checked() { return Boolean.TRUE.equals(data.get("checked")); }

    @Override public String key() { return text("pressed"); }

    @Override public String code() { return text("code"); }

    @Override public boolean ctrl() { return Boolean.TRUE.equals(data.get("ctrl")); }

    @Override public boolean shift() { return Boolean.TRUE.equals(data.get("shift")); }

    @Override public boolean alt() { return Boolean.TRUE.equals(data.get("alt")); }

    @Override public double x() { return number("x"); }

    @Override public double y() { return number("y"); }

    @Override public int button() { return (int) number("button"); }

    @Override public double offsetX() { return number("ox"); }

    @Override public double offsetY() { return number("oy"); }

    @Override public double pressure() { return number("pressure"); }

    @Override public String pointerType() { return text("pointer"); }

    @Override public int clickCount() { return Math.max(1, (int) number("detail")); }

    @Override
    public String field(String name) {
        Object v = data.get("fields") instanceof Map<?, ?> m ? m.get(name) : null;
        return v == null ? null : String.valueOf(v);
    }

    @Override
    public Map<String, String> fields() {
        Map<String, String> out = new LinkedHashMap<>();
        if (data.get("fields") instanceof Map<?, ?> m) m.forEach((k, v) -> out.put(String.valueOf(k), v == null ? null : String.valueOf(v)));
        return java.util.Collections.unmodifiableMap(out);
    }

    private String text(String name) {
        Object v = data.get(name);
        return v == null ? "" : String.valueOf(v);
    }

    private double number(String name) {
        return data.get(name) instanceof Number n ? n.doubleValue() : 0;
    }

    @Override
    public String toString() {
        return "HtmlEvent[" + type + " on " + source + "]";
    }
}
