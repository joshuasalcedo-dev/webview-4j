package io.joshuasalcedo.webview4j.explorer;

import io.joshuasalcedo.webview4j.ui.Component;
import java.util.List;

/**
 * One example of a component in the explorer: what it shows (its {@link #showcase}), the code that makes it (read from
 * this class's source), and the {@link #variables} a reader can change to see it again. Write one class per story in
 * {@code io.joshuasalcedo.webview4j.explorer.stories}; the explorer finds it, puts it on its component's page, and
 * makes everything else (the page, the controls, the API reference).
 * <pre>{@code
 * public final class ButtonVariants implements Story {
 *
 *     static final Variable<String> LABEL = Variable.text("Label", "Button");
 *     static final Variable<Boolean> DISABLED = Variable.flag("Disabled", false);
 *
 *     public Class<?> component() { return Button.class; }
 *     public String title() { return "Variants"; }
 *     public List<Variable<?>> variables() { return List.of(LABEL, DISABLED); }
 *
 *     public Component<?> showcase(Args args) {
 *         return row(
 *                 button(args.get(LABEL)).disabled(args.get(DISABLED)),
 *                 button(args.get(LABEL)).accent().disabled(args.get(DISABLED))
 *         ).gap(8);
 *     }
 * }
 * }</pre>
 * Saving the file while the explorer runs compiles it and shows it again.
 */
public interface Story {

    /** The component whose page it is on: {@code Button.class}. */
    Class<?> component();

    /** Its title on the page: "Default", "Variants", "With an icon"... */
    String title();

    /** What it shows, made from the variables' values now. */
    Component<?> showcase(Args args);

    /** What can be changed: each is a control under the showcase. None by default. */
    default List<Variable<?>> variables() {
        return List.of();
    }

    /** Where it is on its page, lowest first (then by title): the first is the page's main preview. */
    default int order() {
        return 0;
    }

    /** A sentence under its title. */
    default String description() {
        return "";
    }
}
