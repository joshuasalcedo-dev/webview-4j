package io.joshuasalcedo.swaswa.component;

/**
 * Sound or video played (HTML's {@code <audio>} and {@code <video>}, JavaFX's {@code MediaView}): a media player.
 * Every library's is one (<audio>, <video> and its counterparts), so code that takes one takes any of them.
 *
 * @param <Self> its own class
 */
public interface MediaView<Self extends MediaView<Self>> extends Component<Self> {}
