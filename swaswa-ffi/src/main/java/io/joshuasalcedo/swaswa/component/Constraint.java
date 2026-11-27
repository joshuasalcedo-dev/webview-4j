package io.joshuasalcedo.swaswa.component;

import java.util.Objects;

/**
 * A child's place in its container's {@link Layout}: {@link BorderLayout#NORTH}, {@link GridLayout#cell}, an
 * {@link AbsoluteLayout.Bounds}, {@link BoxLayout#GROW}, or a name ({@link #named}: a card's, or Swing's
 * {@code "North"}). Typed, so a component is never taken for one.
 */
public interface Constraint {

    /** A place by name: a {@link CardLayout} card's, or a {@link BorderLayout} region's as Swing names them. */
    static Named named(String name) {
        return new Named(name);
    }

    record Named(String name) implements Constraint {
        public Named {
            Objects.requireNonNull(name, "name");
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
