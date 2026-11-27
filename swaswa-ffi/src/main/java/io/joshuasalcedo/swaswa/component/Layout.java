package io.joshuasalcedo.swaswa.component;

import io.joshuasalcedo.swaswa.css.Style;

/**
 * How a {@link Container} places what it holds: Swing's {@code LayoutManager}, JavaFX's panes. Written as CSS: the
 * container's style ({@code display: grid}...) and each child's ({@code grid-area}...), so the page lays them out
 * itself, as the window sizes.
 *
 * <p>Swing's: {@link FlowLayout}, {@link BoxLayout}, {@link BorderLayout}, {@link GridLayout}, {@link CardLayout};
 * JavaFX's: {@link BoxLayout#row HBox} and {@link BoxLayout#column VBox}, {@link StackLayout StackPane},
 * {@link GridLayout#cell GridPane}'s cells, {@link AbsoluteLayout Pane}.
 */
public interface Layout {

    /** The container's own style, for this layout. */
    void layOut(Style container);

    /** A child's style: {@code constraint} is what it was added with (null if nothing), {@code index} its place. */
    default void place(Style child, Constraint constraint, int index) {}

    /** A child left the container (or the layout was changed): what this layout keeps of it goes. */
    default void removed(Style child) {}
}
