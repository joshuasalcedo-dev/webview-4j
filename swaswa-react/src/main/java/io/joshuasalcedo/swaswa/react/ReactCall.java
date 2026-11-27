package io.joshuasalcedo.swaswa.react;

import io.joshuasalcedo.swaswa.internal.Typed;
import java.util.List;
import java.util.Objects;

/**
 * A call from a React component to Java, through its adapter ({@link ReactAdapter}): which function it called
 * ({@code "commands.sendMessage"}) and with what, read as the app's own types.
 */
public final class ReactCall {

    private final String method;
    private final List<Object> args;

    ReactCall(String method, List<Object> args) {
        this.method = Objects.requireNonNull(method, "method");
        this.args = args == null ? List.of() : args;
    }

    /** The function called, by its path in the adapter: {@code "queries.loadMessages"}. */
    public String method() {
        return method;
    }

    /** How many arguments it was called with. */
    public int argCount() {
        return args.size();
    }

    /** Argument {@code index} as {@code type} (a record of its fields, text, a number...); null when not given. */
    public <T> T arg(int index, Class<T> type) {
        if (index >= args.size()) return null;
        Object v = args.get(index);
        return v == null ? null : type.cast(Typed.as(v, type, method + "[" + index + "]"));
    }

    @Override
    public String toString() {
        return "ReactCall[" + method + " " + args + "]";
    }
}
