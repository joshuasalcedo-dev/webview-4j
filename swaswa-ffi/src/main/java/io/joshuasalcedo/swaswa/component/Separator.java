package io.joshuasalcedo.swaswa.component;

/**
 * A line between things (HTML's {@code <hr>}, JavaFX's {@code Separator}): every library's separator.
 * Every library's is one (<hr> and its counterparts), so code that takes one takes any of them.
 *
 * @param <Self> its own class
 */
public interface Separator<Self extends Separator<Self>> extends Component<Self> {}
