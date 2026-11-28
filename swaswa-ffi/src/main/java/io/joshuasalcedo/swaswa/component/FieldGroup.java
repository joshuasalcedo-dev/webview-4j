package io.joshuasalcedo.swaswa.component;

/**
 * Fields that belong together, with their legend (HTML's {@code <fieldset>}): a field set.
 * Every library's is one (<fieldset> and its counterparts), so code that takes one takes any of them.
 *
 * @param <Self> its own class
 */
public interface FieldGroup<Self extends FieldGroup<Self>> extends Component<Self> {}
