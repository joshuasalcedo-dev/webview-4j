package io.joshuasalcedo.swaswa.component;

import io.joshuasalcedo.swaswa.css.CssProperty;
import io.joshuasalcedo.swaswa.css.Style;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Swing's {@code CardLayout}: one child shown at a time, by name; the others are there but hidden (a wizard's steps, a
 * tab's pages). Each child is added with its name; the first added shows until {@link #show} says otherwise.
 * <pre>{@code
 * CardLayout cards = new CardLayout();
 * pages.setLayout(cards);
 * pages.add(home, "home");
 * pages.add(settings, "settings");
 * cards.show("settings");
 * }</pre>
 */
public final class CardLayout implements Layout {

    private final Map<String, Style> cards = new LinkedHashMap<>();
    private String shown;

    @Override
    public void layOut(Style c) {
        c.set(CssProperty.DISPLAY, "grid");
    }

    @Override
    public synchronized void place(Style child, Constraint constraint, int index) {
        if (!(constraint instanceof Constraint.Named(String name))) throw new IllegalArgumentException("a CardLayout child is added with its name: add(page, \"home\")");
        cards.put(name, child);
        if (shown == null) shown = name;
        child.set(CssProperty.DISPLAY, name.equals(shown) ? null : "none");
    }

    @Override
    public synchronized void removed(Style child) {
        cards.values().remove(child);
        child.set(CssProperty.DISPLAY, null);
        if (shown != null && !cards.containsKey(shown)) {
            shown = cards.isEmpty() ? null : cards.keySet().iterator().next();
            show();
        }
    }

    /** Shows the child named {@code name}, the others hidden. */
    public synchronized void show(String name) {
        if (!cards.containsKey(name)) throw new IllegalArgumentException("no card named " + name + ": " + cards.keySet());
        shown = name;
        show();
    }

    private void show() {
        cards.forEach((n, style) -> style.set(CssProperty.DISPLAY, n.equals(shown) ? null : "none"));
    }

    public synchronized void first() { if (!cards.isEmpty()) show(cards.keySet().iterator().next()); }

    public synchronized void next() { step(1); }

    public synchronized void previous() { step(-1); }

    private void step(int by) {
        java.util.List<String> names = java.util.List.copyOf(cards.keySet());
        if (names.isEmpty()) return;
        int i = Math.max(0, names.indexOf(shown));
        show(names.get(Math.floorMod(i + by, names.size())));
    }

    /** The name of the child shown; null if none. */
    public synchronized String getShown() {
        return shown;
    }
}
