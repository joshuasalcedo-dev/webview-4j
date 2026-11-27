package io.joshuasalcedo.swaswa.state;

import io.joshuasalcedo.swaswa.ffi.NativeStore;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Where a session's state is kept on the person's device: keys and bytes. On the desktop a file in the native
 * library's redb store ({@link NativeStore}); on the web, the browser's; in tests, {@link #memory()}. What the bytes
 * mean is the state's: a storage only keeps them, a write all there or not at all.
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

    /** The desktop's: {@code store}, the native library's redb store (one file of keys and bytes). */
    static StateStorage of(NativeStore store) {
        Objects.requireNonNull(store, "store");
        return new StateStorage() {
            @Override public void put(String key, byte[] value) { store.put(key, value); }
            @Override public Optional<byte[]> get(String key) { return store.get(key); }
            @Override public boolean delete(String key) { return store.delete(key); }
            @Override public List<String> keys(String prefix) { return store.keys(prefix); }
            @Override public void close() { store.close(); }
        };
    }

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
