package io.joshuasalcedo.swaswa.component;

import java.util.List;
import java.util.Optional;

/**
 * A component that holds components and lays them out: Swing's {@code Container} and {@code JPanel}, JavaFX's
 * {@code Pane}. Not necessarily a window: a panel, a row, a card are containers, and a container is a component, so
 * they nest. Its {@link Layout} places what it holds; {@link #add(Object, Constraint)} gives one a place in it
 * ({@code add(toolbar, BorderLayout.NORTH)}).
 *
 * <p>A {@link #setDropTarget drop target} takes in a {@link Component#setDraggable draggable} component let go on it.
 *
 * @param <Self> the container's own type
 * @param <C>    what it holds (an HTML element holds HTML nodes: elements and text)
 */
public interface Container<Self extends Container<Self, C>, C> extends Component<Self> {

    /** Adds these at its end. */
    @SuppressWarnings("unchecked")
    Self add(C... children);

    /** Adds this at {@code index} among what it holds. */
    Self add(C child, int index);

    /** Adds this at its end, in the place {@code constraint} gives it in its layout ({@code BorderLayout.NORTH}, a grid cell...). */
    Self add(C child, Constraint constraint);

    /** Adds this at its end in the place named {@code name}: a card's, or Swing's {@code "North"}. */
    default Self add(C child, String name) {
        return add(child, Constraint.named(name));
    }

    Self remove(C child);

    Self removeAll();

    /** What it holds, in order. */
    List<C> getComponents();

    default int getComponentCount() {
        return getComponents().size();
    }

    /** How it places what it holds: {@link FlowLayout}, {@link BoxLayout}, {@link BorderLayout}, {@link GridLayout}... */
    Self setLayout(Layout layout);

    Layout getLayout();

    /**
     * Whether it takes in a draggable component let go on it (off unless set). While one is dragged over it, it shows
     * so ({@code data-swaswa-drop-over} on its element, for CSS); let go, the component moves into it, then its
     * {@link #addDropListener drop listeners} are told.
     */
    Self setDropTarget(boolean dropTarget);

    boolean isDropTarget();

    /** Something was let go on it: {@link Event#dragged()} is what, if it is a component of the app's. */
    default Self addDropListener(Listener listener) {
        return on("drop", listener);
    }

    default Self removeDropListener(Listener listener) {
        return off("drop", listener);
    }

    /**
     * The first component inside it (at any depth) that {@code selector} matches: JavaFX's {@code lookup}. A selector is
     * {@code #id}, {@code .class}, a tag ({@code button}), or a tag and a class ({@code button.primary}).
     */
    Optional<Component<?>> lookup(String selector);

    /** All the components inside it that {@code selector} matches, in order. */
    List<Component<?>> lookupAll(String selector);
}
