package io.joshuasalcedo.swaswa.component;

/**
 * One item of a {@link ListView}: HTML's {@code <li>}, shadcn/ui's item, swaswa-ui's list item.
 *
 * @param <Self> its own class
 */
public interface ListItem<Self extends ListItem<Self>> extends Component<Self> {}
