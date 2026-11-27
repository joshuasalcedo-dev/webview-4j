package io.joshuasalcedo.swaswa.component;

import java.util.Map;

/**
 * Something that happened to a component: its {@code type} as the DOM names events ({@code click}, {@code input},
 * {@code keydown}, {@code drop}...), and what came with it. When a listener runs, the component already holds what the
 * user typed or picked.
 */
public interface Event {

    /** The component it happened to. */
    Component<?> source();

    /** {@code click}, {@code input}, {@code keydown}... */
    String type();

    /** What a form control holds, as the event left it ("" if none). */
    String value();

    /** Whether a check box or radio button is checked. */
    boolean checked();

    /** A key event's key, as the DOM names it ({@code "Enter"}, {@code "a"}); "" for others. */
    String key();

    /** A key event's physical key ({@code "KeyA"}, {@code "Enter"}): the same whatever the keyboard's language. */
    String code();

    boolean ctrl();

    boolean shift();

    boolean alt();

    /** A pointer event's place (CSS pixels from the page's top left); 0 for others. */
    double x();

    double y();

    /** A pointer event's place in the component it happened to (CSS pixels from its top left, inside its border). */
    double offsetX();

    double offsetY();

    /** A pointer event's button: 0 the main one, 2 the other. */
    int button();

    /** A pen's pressure (0 to 1); 0.5 for a mouse held down, 0 for one up. */
    double pressure();

    /** What a pointer event came from: {@code "mouse"}, {@code "pen"} or {@code "touch"}; "" for other events. */
    String pointerType();

    /** How many clicks in a row (a double click: 2). */
    int clickCount();

    /** A form's field when it was submitted; null if it has none of that name. */
    String field(String name);

    /** A form's fields when it was submitted, by name. */
    Map<String, String> fields();

    /** A drop's component: the one that was dragged here; null for other events, or one from outside the app. */
    default Component<?> dragged() {
        return null;
    }
}
