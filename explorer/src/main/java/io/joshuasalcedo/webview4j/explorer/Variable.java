package io.joshuasalcedo.webview4j.explorer;

import java.util.Objects;

/**
 * Something a {@link Story} lets the reader change, shown as a control under it: text, a switch, a number, or one of
 * an enum's constants. Keep each in a constant and read it in {@link Story#showcase} with {@link Args#get}.
 * <pre>{@code
 * static final Variable<String> LABEL = Variable.text("Label", "Button");
 * static final Variable<Boolean> DISABLED = Variable.flag("Disabled", false);
 * static final Variable<Double> SIZE = Variable.number("Size", 32, 16, 96, 4);
 * static final Variable<Tone> TONE = Variable.choice("Tone", Tone.class, Tone.DEFAULT);
 * }</pre>
 *
 * @param <T> the type of its value
 */
public sealed interface Variable<T> {

    /** Its name: the control's label. */
    String name();

    /** Its value until the reader changes it. */
    T initial();

    /** A text field. */
    record Text(String name, String initial) implements Variable<String> {
        public Text {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(initial, "initial");
        }
    }

    /** A switch. */
    record Flag(String name, Boolean initial) implements Variable<Boolean> {
        public Flag {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(initial, "initial");
        }
    }

    /** A slider from {@code min} to {@code max}. */
    record Number(String name, Double initial, double min, double max, double step) implements Variable<Double> {
        public Number {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(initial, "initial");
            if (min > max) throw new IllegalArgumentException("min > max");
        }
    }

    /** One of {@code type}'s constants. */
    record Choice<E extends Enum<E>>(String name, Class<E> type, E initial) implements Variable<E> {
        public Choice {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(initial, "initial");
        }
    }

    static Variable<String> text(String name, String initial) {
        return new Text(name, initial);
    }

    static Variable<Boolean> flag(String name, boolean initial) {
        return new Flag(name, initial);
    }

    static Variable<Double> number(String name, double initial, double min, double max, double step) {
        return new Number(name, initial, min, max, step);
    }

    static <E extends Enum<E>> Variable<E> choice(String name, Class<E> type, E initial) {
        return new Choice<>(name, type, initial);
    }
}
