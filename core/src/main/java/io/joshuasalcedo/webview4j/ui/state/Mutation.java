package io.joshuasalcedo.webview4j.ui.state;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * A change sent to a server, as TanStack Query's mutations: run in the background, pending while it runs (a button
 * can show it), its error kept to show, and when it succeeds the server state it changed is marked changed
 * ({@link #invalidates}), so the screens showing it fetch it again. It can change server state at once and put it
 * back if the server says no ({@link #optimistic}).
 * <pre>{@code
 * static final Mutation<List<String>> SHIP = Mutation.of((List<String> ids) -> api.ship(ids)).invalidates("orders.>");
 *
 * button("Ship").disabled(SHIP.pending()).onClick(() -> SHIP.run(chosen))
 * }</pre>
 */
public final class Mutation<A> {

    /** What the change does: given its argument, it sends it (and returns what the server answered, or null). */
    @FunctionalInterface
    public interface Action<A> {
        Object run(A argument) throws Exception;
    }

    private record Optimism<A, T>(ServerState<T> state, BiFunction<T, A, T> change) {}

    private final Action<A> action;
    private final List<String> invalidates = new ArrayList<>();
    private final List<Optimism<A, ?>> optimistic = new ArrayList<>();
    private volatile Consumer<Object> onSuccess = r -> {};
    private final String id = "mutation." + System.identityHashCode(this);

    private Mutation(Action<A> action) {
        this.action = Objects.requireNonNull(action, "action");
    }

    public static <A> Mutation<A> of(Action<A> action) {
        return new Mutation<>(action);
    }

    /** When it succeeds, the server state whose keys match these patterns is marked changed. */
    public Mutation<A> invalidates(String... patterns) {
        invalidates.addAll(List.of(patterns));
        return this;
    }

    /** Changes {@code state} at once, as the server will; put back if the server says no. */
    public <T> Mutation<A> optimistic(ServerState<T> state, BiFunction<T, A, T> change) {
        optimistic.add(new Optimism<>(state, change));
        return this;
    }

    /** Done when it succeeded, with what the server answered. */
    public Mutation<A> onSuccess(Consumer<Object> action) {
        this.onSuccess = Objects.requireNonNull(action, "action");
        return this;
    }

    /** Sends it, in the background: the future completes with what the server answered, or its failure. */
    public CompletableFuture<Object> run(A argument) {
        Session session = Session.current();
        Flag flag = flag(session);
        flag.set(true, null);
        List<Runnable> undo = new ArrayList<>();
        for (Optimism<A, ?> o : optimistic) undo.add(apply(o, argument));
        CompletableFuture<Object> done = new CompletableFuture<>();
        Thread.ofVirtual().name("webview4j-" + id).start(() -> session.call(() -> {
            try {
                Object answer = action.run(argument);
                for (String p : invalidates) ServerState.invalidate(p);
                flag.set(false, null);
                onSuccess.accept(answer);
                done.complete(answer);
            } catch (Throwable e) {
                undo.forEach(Runnable::run);
                flag.set(false, e);
                done.completeExceptionally(e);
            }
            return null;
        }));
        return done;
    }

    private static <A, T> Runnable apply(Optimism<A, T> o, A argument) {
        Optional<T> before = o.state().data();
        before.ifPresent(v -> o.state().set(o.change().apply(v, argument)));
        return () -> before.ifPresent(v -> o.state().set(v));
    }

    /** Running now (in this session): to disable a button, show a spinner. */
    public boolean pending() {
        Flag f = flag(Session.current());
        f.observe();
        return f.pending;
    }

    /** What went wrong the last time, if it did. */
    public Optional<Throwable> error() {
        Flag f = flag(Session.current());
        f.observe();
        return Optional.ofNullable(f.error);
    }

    private Flag flag(Session session) {
        SessionState state = SessionState.of(session);
        return state.cell(id, k -> new Flag(k, state));
    }

    /** Pending and the last error, in one session. */
    private static final class Flag extends Cell {
        volatile boolean pending;
        volatile Throwable error;

        Flag(String key, SessionState session) {
            super(key, session);
        }

        void set(boolean pending, Throwable error) {
            this.pending = pending;
            this.error = error;
            changed();
        }

        @Override
        Property property() {
            return Property.DEFAULT;
        }

        @Override
        void evict() {
            if (!pending) error = null;
        }
    }
}
