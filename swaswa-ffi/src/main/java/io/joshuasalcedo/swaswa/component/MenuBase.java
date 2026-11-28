package io.joshuasalcedo.swaswa.component;

/**
 * Commands to pick from (HTML's {@code <menu>}, Swing's {@code JMenu}): a dropdown menu, a context menu, a menu bar.
 * Every library's is one (<menu> and its counterparts), so code that takes one takes any of them.
 *
 * @param <Self> its own class
 */
public interface MenuBase<Self extends MenuBase<Self>> extends Component<Self> {}
