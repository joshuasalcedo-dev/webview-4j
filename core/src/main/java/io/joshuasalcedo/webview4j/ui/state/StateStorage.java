package io.joshuasalcedo.webview4j.ui.state;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Where a session's state is kept on the person's device: keys and bytes. On the desktop it is a file in the native
 * library's redb store ({@code NativeStore}); on the web, the browser's IndexedDB; in tests, {@link #memory()}.
 * What the bytes mean is {@code ui.state}'s: each implementation only keeps them, a write all there or not at all.
 */
public interface StateStorage extends AutoCloseable {

    void put(String key, byte[] value);

    Optional<byte[]> get(String key);

    /** Whether there was one to delete. */
    boolean delete(String key);

    /** The keys starting with {@code prefix}, in order. */
    List<String> keys(String prefix);

    @Override
    default void close() {}

    /** Kept in memory only, gone when the process ends: for tests, and for state nothing should outlive. */
    static StateStorage memory() {
        return new StateStorage() {
            private final ConcurrentSkipListMap<String, byte[]> entries = new ConcurrentSkipListMap<>();

            @Override public void put(String key, byte[] value) { entries.put(key, value.clone()); }
            @Override public Optional<byte[]> get(String key) { return Optional.ofNullable(entries.get(key)).map(byte[]::clone); }
            @Override public boolean delete(String key) { return entries.remove(key) != null; }

            @Override
            public List<String> keys(String prefix) {
                Map<String, byte[]> tail = new TreeMap<>(entries.tailMap(prefix));
                return tail.keySet().stream().takeWhile(k -> k.startsWith(prefix)).toList();
            }
        };
    }
}
