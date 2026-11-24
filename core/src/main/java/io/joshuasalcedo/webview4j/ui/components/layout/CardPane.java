package io.joshuasalcedo.webview4j.ui.components.layout;

import io.joshuasalcedo.webview4j.ui.Component;
import io.joshuasalcedo.webview4j.ui.components.Element;
import java.util.Locale;

/**
 * One child shown at a time, chosen by its name, as Swing's {@code CardLayout}: the steps of a wizard, the pages
 * behind a side bar. The others stay in the page, hidden, so what was typed in them stays.
 * <pre>{@code
 * new CardPane().card("account", accountForm).card("plan", planChooser).card("done", summary)
 *         .show(step)
 * }</pre>
 */
public final class CardPane extends Pane<CardPane> {

    /** How it moves from one card to the next. */
    public enum Transition { NONE, FADE, SLIDE }

    public CardPane() {
        super("cardpane");
    }

    /** A card and its name. */
    public CardPane card(String name, Component<?> content) {
        return add(new Element("card").set("name", name).with(content));
    }

    /** The card shown, by its name (the first one unless set; Swing's {@code show(parent, name)}). */
    public CardPane show(String name) {
        return prop("shown", name);
    }

    public CardPane transition(Transition transition) {
        return prop("transition", transition.name().toLowerCase(Locale.ROOT));
    }
}
