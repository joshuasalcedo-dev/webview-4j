package io.joshuasalcedo.swaswa.component;

/**
 * Items one after another (JavaFX's {@code ListView}, Swing's {@code JList}): HTML's {@code <ul>} and {@code <ol>},
 * shadcn/ui's item group, swaswa-ui's lists. Its items are its children ({@link ListItem}s, or what the list draws
 * from its data).
 *
 * @param <Self> its own class
 */
public interface ListView<Self extends ListView<Self>> extends Component<Self> {}
