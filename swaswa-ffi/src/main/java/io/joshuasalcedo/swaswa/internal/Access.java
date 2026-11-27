package io.joshuasalcedo.swaswa.internal;

import java.util.Map;
import java.util.Objects;

/**
 * What one of swaswa's packages lets another use without making it the API: set by the package that has it, when its
 * class loads. Inside the module only (this package is not exported).
 */
public final class Access {

    private Access() {}

    /** Who shows a style or a style sheet is told of its changes. */
    public interface Looks {
        void listen(Object styleOrSheet, Runnable changed);

        void unlisten(Object styleOrSheet, Runnable changed);
    }

    /** A page's call, as the page sent it. */
    public interface Calls {
        Map<String, Object> fields(Object invocation);
    }

    private static volatile Looks looks;
    private static volatile Calls calls;

    public static void looks(Looks given) { looks = Objects.requireNonNull(given); }

    public static void calls(Calls given) { calls = Objects.requireNonNull(given); }

    public static Looks looks() {
        if (looks == null) load("io.joshuasalcedo.swaswa.css.Style");
        return looks;
    }

    public static Calls calls() {
        if (calls == null) load("io.joshuasalcedo.swaswa.ffi.Invocation");
        return calls;
    }

    private static void load(String type) {
        try {
            Class.forName(type, true, Access.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }
}
