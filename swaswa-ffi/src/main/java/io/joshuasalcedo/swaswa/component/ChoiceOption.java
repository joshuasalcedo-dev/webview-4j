package io.joshuasalcedo.swaswa.component;

/**
 * One of a {@link Choice}'s options (HTML's {@code <option>}): a select's, a combobox's, a radio group's item.
 * Every library's is one (<option> and its counterparts), so code that takes one takes any of them.
 *
 * @param <Self> its own class
 */
public interface ChoiceOption<Self extends ChoiceOption<Self>> extends Component<Self> {}
