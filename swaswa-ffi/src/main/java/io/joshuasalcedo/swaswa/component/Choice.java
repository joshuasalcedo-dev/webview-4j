package io.joshuasalcedo.swaswa.component;

/**
 * One of several, as the person picks it: a select, tabs, a segmented control, radio buttons (Swing's
 * {@code JComboBox}, JavaFX's {@code ChoiceBox}). Every library's is one; its value is the chosen one's.
 * <pre>{@code
 * Choice<?> show = new ShadcnTabs(...);              // or new UiSegmentedControl(), or new Select(...)
 * show.valueProperty().addListener((was, now) -> showOrders(now));
 * }</pre>
 *
 * @param <Self> the choice's own class
 */
public interface Choice<Self extends Choice<Self>> extends Component<Self> {

    /** The chosen one's value, live: what the person picks, at once; set it, or bind a state to it. */
    Property<String> valueProperty();

    Self setValue(String value);
}
