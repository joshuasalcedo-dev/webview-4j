package io.joshuasalcedo.swaswa.state;

import io.joshuasalcedo.swaswa.component.Property;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * A change sent to a server, as TanStack Query's mutations: run in the background, pending while it runs (a button
 * can follow it), its error kept to show, and when it succeeds the server state it changed is marked changed
 * ({@link #invalidates}), so what shows it fetches it again. It can change server state at once and put it back if the
 * server says no ({@link #optimistic}).
 * <pre>{@code
 * static final Mutation<List<String>> SHIP = Mutation.of((List<String> ids) -> api.ship(ids)).invalidates("orders.>");
 *
 * ship.enabledProperty().bind(SHIP.pending(session).map(p -> !p));
 * ship.addActionListener(e -> SHIP.run(session, chosen));
 * }</pre>
 *
 * @param <A> what it is given to send
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
    public CompletableFuture<Object> run(Session session, A argument) {
        Flag flag = flag(session);
        flag.pendingNow.set(true);
        flag.errorNow.set(null);
        List<Runnable> undo = new ArrayList<>();
        for (Optimism<A, ?> o : optimistic) undo.add(apply(session, o, argument));
        CompletableFuture<Object> done = new CompletableFuture<>();
        Thread.ofVirtual().name("swaswa-" + id).start(() -> {
            try {
                Object answer = action.run(argument);
                for (String p : invalidates) session.invalidate(p);
                flag.pendingNow.set(false);
                onSuccess.accept(answer);
                done.complete(answer);
            } catch (Throwable e) {
                undo.forEach(Runnable::run);
                flag.errorNow.set(e);
                flag.pendingNow.set(false);
                done.completeExceptionally(e);
            }
        });
        return done;
    }

    private static <A, T> Runnable apply(Session session, Optimism<A, T> o, A argument) {
        T before = o.state().cell(session).data.get();
        if (before == null) return () -> {};
        o.state().set(session, o.change().apply(before, argument));
        return () -> o.state().set(session, before);
    }

    /** Running now, in {@code session}: to disable a button, show a spinner. */
    public Property<Boolean> pending(Session session) {
        return flag(session).pending;
    }

    /** What went wrong the last time; null when it worked. */
    public Property<Throwable> error(Session session) {
        return flag(session).error;
    }

    private Flag flag(Session session) {
        Objects.requireNonNull(session, "session");
        return session.cell(id, k -> new Flag(k, session));
    }

    /** Pending and the last error, in one session. */
    private static final class Flag extends Cell {
        final Property<Boolean> pendingNow = new Property<>(false);
        final Property<Throwable> errorNow = new Property<>();
        final Property<Boolean> pending = pendingNow.map(v -> v);
        final Property<Throwable> error = errorNow.map(v -> v);

        Flag(String key, Session session) {
            super(key, session);
        }

        @Override
        boolean shown() {
            return listened(pending, error);
        }

        @Override
        Lifetime lifetime() {
            return Lifetime.DEFAULT;
        }

        @Override
        void evict() {
            if (!pendingNow.get()) errorNow.set(null);
        }
    }
}
