package io.joshuasalcedo.webview4j.explorer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** The values of a {@link Story}'s {@link Variable}s now: as set by the explorer's controls, or their initial ones. */
public final class Args {

    private final Map<Variable<?>, Object> values = new LinkedHashMap<>();

    /** Each of {@code variables} at its initial value. */
    public static Args of(List<Variable<?>> variables) {
        Args args = new Args();
        for (Variable<?> v : variables) args.values.put(v, v.initial());
        return args;
    }

    /** {@code variable}'s value now (its initial one if the story does not list it). */
    @SuppressWarnings("unchecked")
    public <T> T get(Variable<T> variable) {
        Objects.requireNonNull(variable, "variable");
        Object value = values.get(variable);
        return value == null ? variable.initial() : (T) value;
    }

    /** Sets {@code variable}'s value: a control changed. */
    public <T> Args set(Variable<T> variable, T value) {
        values.put(Objects.requireNonNull(variable, "variable"), Objects.requireNonNull(value, "value"));
        return this;
    }

    /** Sets a value whose type the caller has checked (the explorer's controls). */
    Args setUnchecked(Variable<?> variable, Object value) {
        values.put(variable, value);
        return this;
    }
}
