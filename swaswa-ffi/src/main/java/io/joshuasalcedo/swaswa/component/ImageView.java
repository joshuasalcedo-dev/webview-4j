package io.joshuasalcedo.swaswa.component;

/**
 * A picture (HTML's {@code <img>}, JavaFX's {@code ImageView}): an avatar, a thumbnail.
 * Every library's is one (<img> and its counterparts), so code that takes one takes any of them.
 *
 * @param <Self> its own class
 */
public interface ImageView<Self extends ImageView<Self>> extends Component<Self> {}
