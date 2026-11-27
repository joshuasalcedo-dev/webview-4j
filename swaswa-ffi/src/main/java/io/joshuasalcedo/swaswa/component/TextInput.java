package io.joshuasalcedo.swaswa.component;

/**
 * Text the person types: a text field, a text area, a search field (Swing's {@code JTextComponent}, JavaFX's
 * {@code TextInputControl}). Every library's is one: HTML's {@code <input>}, shadcn/ui's, swaswa-ui's.
 * <pre>{@code
 * TextInput<?> name = new UiTextField();             // or new ShadcnInput(), or new Input()
 * name.setPlaceholder("Ana Reyes");
 * name.valueProperty().addListener((was, now) -> check(now));
 * }</pre>
 *
 * @param <Self> the input's own class
 */
public interface TextInput<Self extends TextInput<Self>> extends Component<Self> {

    /** What it holds, live: what the person types, at once; set it, or bind a state to it. */
    Property<String> valueProperty();

    Self setValue(String value);

    /** What it shows while empty. */
    Self setPlaceholder(String text);
}
