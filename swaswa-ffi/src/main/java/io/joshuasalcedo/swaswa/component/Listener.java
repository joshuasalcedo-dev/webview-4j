package io.joshuasalcedo.swaswa.component;

/**
 * What runs when a component's event comes ({@link Component#on}, {@code addActionListener}...): on a thread of its
 * own, not the page's or the event loop's.
 */
@FunctionalInterface
public interface Listener {

    void handle(Event event) throws Exception;
}
