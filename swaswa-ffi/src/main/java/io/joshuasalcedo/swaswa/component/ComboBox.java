package io.joshuasalcedo.swaswa.component;

/**
 * A text input with suggestions to pick from (HTML's {@code <datalist>} for an {@code <input>}, JavaFX's {@code ComboBox}): a combobox, a token field, a command palette.
 * Every library's is one (<datalist> and its counterparts), so code that takes one takes any of them.
 *
 * @param <Self> its own class
 */
public interface ComboBox<Self extends ComboBox<Self>> extends Component<Self> {}
