package io.joshuasalcedo.swaswa.component;

/**
 * Text that names something, perhaps with a graphic: a label, and a button too (JavaFX's {@code Labeled}, Swing's
 * {@code JLabel}). Every library's label is one: HTML's {@code <label>}, shadcn/ui's, swaswa-ui's. Its text is
 * {@link Component}'s ({@code setText}, {@code getText}).
 * <pre>{@code
 * Labeled<?> caption = new ShadcnLabel("Customer");   // or new UiLabel("Customer"), or new Label("Customer")
 * }</pre>
 *
 * @param <Self> its own class
 */
public interface Labeled<Self extends Labeled<Self>> extends Component<Self> {}
