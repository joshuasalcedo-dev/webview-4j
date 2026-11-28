package io.joshuasalcedo.swaswa.state;

import io.joshuasalcedo.swaswa.component.Property;

/**
 * One value in one session (a store's, a server state's, a mutation's): its key, its session, and when something last
 * asked for it. Shown while something listens to one of its properties (a component bound to it).
 */
abstract class Cell {

    final String key;
    final Session session;
    volatile long lastUsed = System.nanoTime();

    Cell(String key, Session session) {
        this.key = key;
        this.session = session;
    }

    final void used() {
        lastUsed = System.nanoTime();
    }

    /** Whether something shows it now: a listener (a bound component) on one of its properties. */
    abstract boolean shown();

    abstract Lifetime lifetime();

    /** Dropped from memory (it stays on disk, if persisted): nothing has shown it for its {@code keepInMemory}. */
    void evict() {}

    /** For the sweeper: polling, and anything else on a timer. */
    void tick() {}

    static boolean listened(Property<?>... properties) {
        for (Property<?> p : properties) if (p.hasListeners()) return true;
        return false;
    }
}
