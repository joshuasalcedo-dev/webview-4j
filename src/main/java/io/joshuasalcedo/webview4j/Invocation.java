package io.joshuasalcedo.webview4j;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A page's call of a command: {@code invoke("users.find", {id: 42})} in the page. {@code args} is
 * what the page passed, as JSON values ({@code Map}, {@code List}, {@code String},
 * {@code Long}/{@code Double}, {@code Boolean}, or null); the helpers read an object's fields.
 */
public record Invocation(View view, String command, Object args) {

    public Invocation {
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(command, "command");
    }

    /** The arguments as an object; empty when the page passed none or something else. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> fields() {
        return args instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    /** A text field. */
    public String text(String name) {
        return fields().get(name) instanceof String s ? s : fail(name, "text");
    }

    /** A whole-number field. */
    public long number(String name) {
        return fields().get(name) instanceof Long n ? n : fail(name, "a whole number");
    }

    /** Any numeric field. */
    public double decimal(String name) {
        return fields().get(name) instanceof Number n ? n.doubleValue() : fail(name, "a number");
    }

    public boolean flag(String name) {
        return fields().get(name) instanceof Boolean b ? b : fail(name, "true or false");
    }

    @SuppressWarnings("unchecked")
    public List<Object> list(String name) {
        return fields().get(name) instanceof List<?> l ? (List<Object>) l : fail(name, "a list");
    }

    /** True when the page passed the field, even as null. */
    public boolean has(String name) {
        return fields().containsKey(name);
    }

    private <T> T fail(String name, String expected) {
        throw new IllegalArgumentException(command + ": argument '" + name + "' must be " + expected);
    }
}
