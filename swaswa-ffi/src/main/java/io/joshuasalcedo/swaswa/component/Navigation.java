package io.joshuasalcedo.swaswa.component;

/**
 * Ways to get around (HTML's {@code <nav>}): a navigation menu, breadcrumbs, pagination, a sidebar.
 * Every library's is one (<nav> and its counterparts), so code that takes one takes any of them.
 *
 * @param <Self> its own class
 */
public interface Navigation<Self extends Navigation<Self>> extends Component<Self> {}
