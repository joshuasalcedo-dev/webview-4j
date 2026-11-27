package io.joshuasalcedo.swaswa.react;

/** Told of an event a React component sent ({@link ReactElement#onEvent}). */
@FunctionalInterface
public interface ReactListener {

    void handle(ReactEvent event);
}
