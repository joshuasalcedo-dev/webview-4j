package io.joshuasalcedo.webview4j.ui.state;

import io.joshuasalcedo.webview4j.Json;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** A value as it is kept on disk: its version, when it was fetched, the server's version tag, and the value. */
record Kept<T>(int version, long at, String etag, T value) {

    byte[] encode(Codec<T> codec) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("v", version);
        out.put("at", at);
        if (etag != null) out.put("etag", etag);
        out.put("data", codec.tree(value));
        return Json.write(out).getBytes(StandardCharsets.UTF_8);
    }

    /** What was kept, if it is of this version and younger than {@code maxAgeMillis}; empty otherwise (or unreadable). */
    static <T> Optional<Kept<T>> decode(byte[] bytes, Codec<T> codec, int version, long maxAgeMillis) {
        try {
            Map<String, Object> m = Json.object(new String(bytes, StandardCharsets.UTF_8));
            int v = ((Number) m.getOrDefault("v", 0)).intValue();
            long at = ((Number) m.getOrDefault("at", 0L)).longValue();
            if (v != version || System.currentTimeMillis() - at > maxAgeMillis) return Optional.empty();
            Object etag = m.get("etag");
            return Optional.of(new Kept<>(v, at, etag == null ? null : etag.toString(), codec.read(m.get("data"))));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}
