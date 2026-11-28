package io.joshuasalcedo.swaswa.state;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * One screen's state kept between runs: the open tab, where a divider is, a table's filters. As a {@link Store}, but
 * on disk unless told otherwise (90 days unless its {@link Lifetime} says), so the screen opens as it was left.
 * <pre>{@code
 * static final Saved<String> TAB = Saved.of("orders.tab", String.class, () -> "open");
 *
 * tabs.selectedProperty().bindBidirectional(TAB.in(session));
 * }</pre>
 *
 * @param <T> what it holds
 */
public final class Saved<T> extends LocalState<T> {

    private static final Lifetime KEPT = Lifetime.DEFAULT.persist(Duration.ofDays(90)).keepInMemory(Duration.ofMinutes(5));

    private Saved(String key, Codec<T> codec, Supplier<T> initial) {
        super(key, codec, initial, KEPT);
    }

    public static <T> Saved<T> of(String key, Class<T> type, Supplier<T> initial) {
        return new Saved<>(key, Codec.of(type), initial);
    }

    public static <T> Saved<T> of(String key, Codec<T> codec, Supplier<T> initial) {
        return new Saved<>(key, codec, initial);
    }

    @Override
    String storageKey() {
        return "saved." + key();
    }

    @Override
    public String toString() {
        return "Saved[" + key() + "]";
    }
}
