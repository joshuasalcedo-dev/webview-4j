package io.joshuasalcedo.swaswa.component;

/**
 * Keys to press (HTML's {@code <kbd>}): a key, a menu item's shortcut.
 * Every library's is one (<kbd> and its counterparts), so code that takes one takes any of them.
 *
 * @param <Self> its own class
 */
public interface KeyboardKey<Self extends KeyboardKey<Self>> extends Component<Self> {}
