package io.joshuasalcedo.swaswa.react;

import io.joshuasalcedo.swaswa.internal.Typed;
import java.util.Map;
import java.util.Objects;

/**
 * An event a React component sent: its prop's name ({@code onChange}, {@code onOpenChange}) and what it was called
 * with. A DOM event comes as its element's value and checked; anything else as itself (JSON: a boolean, a string, an
 * object read as a record).
 */
public final class ReactEvent {

    private final ReactElement<?> source;
    private final String name;
    private final Object payload;

    ReactEvent(ReactElement<?> source, String name, Object payload) {
        this.source = Objects.requireNonNull(source, "source");
        this.name = Objects.requireNonNull(name, "name");
        this.payload = payload;
    }

    /** The component that sent it. */
    public ReactElement<?> source() {
        return source;
    }

    /** The prop it came through: {@code onChange}. */
    public String name() {
        return name;
    }

    /** What it was called with, as {@code type}: {@code event.payload(Boolean.class)}, a record of its fields. */
    public <T> T payload(Class<T> type) {
        return type.cast(Typed.as(payload, type, name));
    }

    /** A DOM event's element's value ({@code <input>}'s text), or the payload itself when it is text; else null. */
    public String value() {
        if (payload instanceof Map<?, ?> m) return m.get("value") == null ? null : String.valueOf(m.get("value"));
        return payload instanceof String s ? s : null;
    }

    /** A DOM event's element's checked, or the payload itself when it is a boolean. */
    public boolean checked() {
        if (payload instanceof Map<?, ?> m) return Boolean.TRUE.equals(m.get("checked"));
        return Boolean.TRUE.equals(payload);
    }

    @Override
    public String toString() {
        return "ReactEvent[" + name + ": " + payload + "]";
    }
}
