package io.joshuasalcedo.swaswa.ffi;

import io.joshuasalcedo.swaswa.internal.Access;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A page's call of a command: {@code invoke("users.find", {id: 42})} in the page. Its arguments are read typed: a field
 * at a time ({@link #text}, {@link #number}, {@link #flag}...), or all at once as a record of the app's own
 * ({@link #as}): {@code record Find(long id) {}}, {@code call.as(Find.class).id()}.
 */
public final class Invocation {

    static {
        Access.calls(invocation -> ((Invocation) invocation).fields());
    }

    private final View view;
    private final String command;
    private final Object args;

    Invocation(View view, String command, Object args) {
        this.view = Objects.requireNonNull(view, "view");
        this.command = Objects.requireNonNull(command, "command");
        this.args = args;
    }

    /** The view whose page called. */
    public View view() {
        return view;
    }

    /** The command it called. */
    public String command() {
        return command;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fields() {
        return args instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    /** A text field. */
    public String text(String name) {
        return fields().get(name) instanceof String s ? s : fail(name, "text");
    }

    /** A text field, or {@code otherwise} if the page did not pass it (or passed null). */
    public String text(String name, String otherwise) {
        return fields().get(name) instanceof String s ? s : otherwise;
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

    /** A list of texts. */
    public List<String> texts(String name) {
        if (!(fields().get(name) instanceof List<?> l)) return fail(name, "a list of texts");
        List<String> out = new ArrayList<>();
        for (Object o : l) out.add(o instanceof String s ? s : fail(name, "a list of texts"));
        return List.copyOf(out);
    }

    /** True when the page passed the field, even as null. */
    public boolean has(String name) {
        return fields().containsKey(name);
    }

    /**
     * All its arguments as a record of the app's own, by its components' names: texts, numbers, true or false, enums
     * (by name), records inside, and lists of those.
     *
     * @throws IllegalArgumentException if what the page passed does not fit
     */
    public <R extends Record> R as(Class<R> type) {
        return io.joshuasalcedo.swaswa.internal.Typed.record(type, fields(), command);
    }

    private <T> T fail(String name, String expected) {
        throw new IllegalArgumentException(command + ": argument '" + name + "' must be " + expected);
    }

    @Override
    public String toString() {
        return "Invocation[" + command + " from " + view.id() + "]";
    }
}
