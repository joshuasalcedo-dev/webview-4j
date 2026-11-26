package io.joshuasalcedo.webview4j.ui;

/**
 * A part of one component as the kit's CSS draws it: its outermost element ({@code ROOT}) or one inside it (a
 * dialog's {@code TITLE}, a button's {@code ICON}). Each component has its own enum of them, made from the kit's CSS
 * when core is built (style-gen, into {@code io.joshuasalcedo.webview4j.ui.style}): {@code ButtonPart},
 * {@code DialogPart}... A component takes only its own parts:
 * <pre>{@code
 * dialog.style(DialogPart.TITLE, Style.of().color(Style.Token.ACCENT));   // compiles
 * button.style(DialogPart.TITLE, ...);                                    // does not
 * new StyleSheet().rule(DialogPart.BODY, Style.of().padding(24));         // every dialog of the window
 * }</pre>
 *
 * @param <C> the component the part belongs to
 */
public interface StylePart<C extends Component<C>> {

    /** The kit's class on the part's element ({@code "nt-dialog-title"}). */
    String className();
}
