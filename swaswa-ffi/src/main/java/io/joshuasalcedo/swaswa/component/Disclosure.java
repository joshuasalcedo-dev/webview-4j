package io.joshuasalcedo.swaswa.component;

/**
 * Content that opens and closes (HTML's {@code <details>}, JavaFX's {@code TitledPane}): a collapsible, an accordion, a titled pane.
 * Every library's is one (<details> and its counterparts), so code that takes one takes any of them.
 *
 * @param <Self> its own class
 */
public interface Disclosure<Self extends Disclosure<Self>> extends Component<Self> {}
