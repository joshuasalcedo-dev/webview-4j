package io.joshuasalcedo.webview4j.ui.components.usecase.collection;

import io.joshuasalcedo.webview4j.ui.Component;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A map of keys to values as a property grid: maps, lists and records inside unfold, values are edited in place,
 * entries added and removed. The Java side of the kit's {@code DataMap}. Paths name a value inside:
 * {@code "database.pool.size"}, {@code "tags.0"}.
 * <pre>{@code
 * DataMap.of("Settings", settings).open(2)
 *         .onEdit(edit -> settings.set(edit.path(), edit.value()))
 *         .onAdd(entry -> settings.put(entry.key(), entry.value()))
 *         .onRemove(path -> settings.remove(path))
 * }</pre>
 */
public final class DataMap extends Component<DataMap> {

    /** A value edited: where, and what it is now (a number, yes/no, text, null, as JSON reads it). */
    public record Edit(String path, Object value) {}

    /** An entry added at the top level. */
    public record Entry(String key, Object value) {}

    /** The map: its values may be maps, lists, records, value objects (their text) and plain values. */
    public DataMap(String label, Map<String, ?> value) {
        super("dataMap");
        prop("label", label);
        prop("value", Rows.value(value));
    }

    public static DataMap of(String label, Map<String, ?> value) {
        return new DataMap(label, value);
    }

    /** A record's components as the map. */
    public static DataMap of(String label, Record value) {
        DataMap map = new DataMap(label, Map.of());
        return map.prop("value", Rows.value(value));
    }

    /** How deep it is open at first (1: the top level). */
    public DataMap open(int depth) { return prop("depth", depth); }

    public DataMap onEdit(Consumer<Edit> action) {
        return on("onEdit", v -> {
            Map<?, ?> m = v instanceof Map<?, ?> map ? map : Map.of();
            action.accept(new Edit(String.valueOf(m.get("path")), m.get("value")));
        });
    }

    public DataMap onAdd(Consumer<Entry> action) {
        return on("onAdd", v -> {
            Map<?, ?> m = v instanceof Map<?, ?> map ? map : Map.of();
            action.accept(new Entry(String.valueOf(m.get("key")), m.get("value")));
        });
    }

    /** An entry removed: its path. */
    public DataMap onRemove(Consumer<String> action) {
        return on("onRemove", v -> action.accept(String.valueOf(v)));
    }
}
