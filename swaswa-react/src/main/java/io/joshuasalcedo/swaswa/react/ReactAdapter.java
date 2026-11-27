package io.joshuasalcedo.swaswa.react;

import java.util.concurrent.CompletableFuture;

/**
 * What a React component calls in Java: its adapter prop (a chat's {@code adapter}: commands, queries, events), each
 * function answered by Java when it can. A library's typed service implements it for the app ({@code ChatService});
 * an app may too.
 */
public interface ReactAdapter {

    /**
     * The component called {@code call.method()}: what it answers (a record, a list, text; null for nothing), when it
     * has it. A failed future fails the component's promise, with its message.
     */
    CompletableFuture<?> call(ReactCall call);

    /**
     * The component listens at {@code method} ({@code "events.subscribe"}): what happens is given to {@code emitter},
     * from any thread, as long as the component is there.
     */
    default void subscribe(String method, ReactEmitter emitter) {}

    /** Gives a component what happened (an event it listens for, as the app's record). */
    @FunctionalInterface
    interface ReactEmitter {
        void emit(Object event);
    }
}
