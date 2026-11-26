package io.joshuasalcedo.swaswa.ffi;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * A page's {@code localStorage} or {@code sessionStorage}, from Java ({@link View#localStorage()},
 * {@link View#sessionStorage()}): what the page keeps for its own origin. Keys and values are text; they go into the
 * page as JSON, never as script.
 */
public final class PageStorage {

    private final View view;
    private final String name;

    PageStorage(View view, String name) {
        this.view = view;
        this.name = name;
    }

    /** {@code localStorage} or {@code sessionStorage}. */
    public String name() {
        return name;
    }

    /** Everything in it, key by key, in the page's order. */
    @SuppressWarnings("unchecked")
    public CompletableFuture<Map<String, String>> entries() {
        String script = "(() => { const s = window." + name + ", out = {}; for (let i = 0; i < s.length; i++) { const k = s.key(i); out[k] = s.getItem(k); } return out; })()";
        return view.eval(script).thenApply(value -> {
            Map<String, String> out = new LinkedHashMap<>();
            if (value instanceof Map<?, ?> map) map.forEach((k, v) -> out.put(String.valueOf(k), v == null ? null : String.valueOf(v)));
            return out;
        });
    }

    /** The value of {@code key}, if there is one. */
    public CompletableFuture<Optional<String>> get(String key) {
        return view.eval("window." + name + ".getItem(" + Json.write(Objects.requireNonNull(key, "key")) + ")")
                .thenApply(v -> Optional.ofNullable(v == null ? null : String.valueOf(v)));
    }

    /** Sets {@code key} to {@code value}. */
    public CompletableFuture<Void> set(String key, String value) {
        return view.eval("window." + name + ".setItem(" + Json.write(Objects.requireNonNull(key, "key")) + ", "
                + Json.write(Objects.requireNonNull(value, "value")) + ")").thenApply(v -> null);
    }

    /** Takes {@code key} out. */
    public CompletableFuture<Void> remove(String key) {
        return view.eval("window." + name + ".removeItem(" + Json.write(Objects.requireNonNull(key, "key")) + ")").thenApply(v -> null);
    }

    /** Empties it. */
    public CompletableFuture<Void> clear() {
        return view.eval("window." + name + ".clear()").thenApply(v -> null);
    }
}
