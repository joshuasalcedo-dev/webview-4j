package io.joshuasalcedo.swaswa.component;

/**
 * A link (HTML's {@code <a>}, JavaFX's {@code Hyperlink}): a link, a breadcrumb's, a page number.
 * Every library's is one (<a> and its counterparts), so code that takes one takes any of them.
 *
 * @param <Self> its own class
 */
public interface Hyperlink<Self extends Hyperlink<Self>> extends Component<Self> {}
