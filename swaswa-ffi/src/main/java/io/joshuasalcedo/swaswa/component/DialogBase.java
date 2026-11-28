package io.joshuasalcedo.swaswa.component;

/**
 * A window over the page (HTML's {@code <dialog>}, JavaFX's {@code Dialog}): a dialog, an alert dialog, a sheet, a drawer.
 * Every library's is one (<dialog> and its counterparts), so code that takes one takes any of them.
 *
 * @param <Self> its own class
 */
public interface DialogBase<Self extends DialogBase<Self>> extends Component<Self> {}
