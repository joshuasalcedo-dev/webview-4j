package io.joshuasalcedo.webview4j.ui.state;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * One screen's state kept between runs: the open tab, where a divider is, a table's filters. As a {@link Store}, but
 * kept on disk unless told otherwise (90 days unless its {@link Property} says), so the screen opens as it was left.
 * <pre>{@code
 * private final Saved<String> tab = Saved.of("orders.tab", String.class, () -> "open");
 *
 * segmented("Show", tabs).value(tab.get()).onChange(tab::set)
 * }</pre>
 */
public final class Saved<T> extends LocalState<T> {

    private static final Property KEPT = Property.DEFAULT.persist(Duration.ofDays(90)).keepInMemory(Duration.ofMinutes(5));

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
