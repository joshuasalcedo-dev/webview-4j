package io.joshuasalcedo.swaswa.component;

/**
 * A button: clicked to act (Swing's {@code AbstractButton}, JavaFX's {@code ButtonBase}). Every library's button is
 * one: HTML's {@code <button>}, shadcn/ui's, swaswa-ui's, a toolbar's; so code that takes a button takes any of them.
 * What it says and does is {@link Component}'s: {@code setText}, {@code addActionListener}, {@code setEnabled}.
 * <pre>{@code
 * ButtonBase<?> save = new ShadcnButton("Save");     // or new UiButton("Save"), or new Button("Save")
 * save.addActionListener(e -> saveOrder());
 * }</pre>
 *
 * @param <Self> the button's own class
 */
public interface ButtonBase<Self extends ButtonBase<Self>> extends Labeled<Self> {}
