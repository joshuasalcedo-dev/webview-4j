package io.joshuasalcedo.swaswa.component;

/**
 * One line of text the person types (Swing's {@code JTextField}, JavaFX's {@code TextField}): HTML's {@code <input>},
 * shadcn/ui's input, swaswa-ui's text, search, email, phone fields... A {@link TextInput}.
 *
 * @param <Self> its own class
 */
public interface TextField<Self extends TextField<Self>> extends TextInput<Self> {}
