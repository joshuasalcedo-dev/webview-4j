package io.joshuasalcedo.swaswa.component;

/**
 * On or off, as the person sets it: a switch, a checkbox, a toggle button (Swing's {@code JToggleButton}, JavaFX's
 * {@code Toggle}). Every library's is one.
 * <pre>{@code
 * Toggle<?> dark = new UiSwitch();                    // or new ShadcnSwitch(), new ShadcnCheckbox()
 * dark.selectedProperty().bindBidirectional(app.state(DARK));
 * }</pre>
 *
 * @param <Self> the toggle's own class
 */
public interface Toggle<Self extends Toggle<Self>> extends Component<Self> {

    /** On or off, live: what the person sets it to, at once; set it, or bind a state to it. */
    Property<Boolean> selectedProperty();

    default boolean isSelected() {
        return Boolean.TRUE.equals(selectedProperty().get());
    }

    @SuppressWarnings("unchecked")
    default Self setSelected(boolean on) {
        selectedProperty().set(on);
        return (Self) this;
    }
}
