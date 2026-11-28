package io.joshuasalcedo.swaswa.component;

/**
 * How far something has come, or how much there is (HTML's {@code <progress>} and {@code <meter>}, JavaFX's {@code ProgressIndicator}): a progress bar, a spinner, a strength meter.
 * Every library's is one (<progress>, <meter> and its counterparts), so code that takes one takes any of them.
 *
 * @param <Self> its own class
 */
public interface ProgressIndicator<Self extends ProgressIndicator<Self>> extends Component<Self> {}
