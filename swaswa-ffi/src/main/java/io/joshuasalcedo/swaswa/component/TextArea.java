package io.joshuasalcedo.swaswa.component;

/**
 * Several lines of text the person types (Swing's {@code JTextArea}, JavaFX's {@code TextArea}): HTML's
 * {@code <textarea>}, shadcn/ui's, swaswa-ui's. A {@link TextInput}.
 *
 * @param <Self> its own class
 */
public interface TextArea<Self extends TextArea<Self>> extends TextInput<Self> {}
