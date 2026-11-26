package io.joshuasalcedo.webview4j.plugin.collection;

import io.joshuasalcedo.webview4j.ui.components.Element;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A tree of records with columns (a tree table): rows open to show their children, chosen, opened, a right-click
 * menu. The Java side of the kit's {@code DataTree}. A record's children are its {@code children} component (a list of
 * the same records); a row whose children are not loaded yet says so ({@code hasChildren}), and {@link #onExpand} loads
 * them when it is opened.
 * <pre>{@code
 * record Team(String id, String name, int people, Money budget, List<Team> children) {}
 *
 * DataTree.of("Teams", teams)                  // a column per component but the children
 *         .onRowOpen(id -> page.navigate("/teams/" + id))
 * }</pre>
 */
public final class DataTree extends CollectionView<DataTree> {

    private final List<Map<String, Object>> columns = new ArrayList<>();

    public DataTree(String label) {
        super("dataTree", label);
        prop("columns", columns);
    }

    /** A tree of {@code records}: a column for each component but {@code children} (and {@code hasChildren}). */
    @SuppressWarnings("unchecked")
    public static <R extends Record> DataTree of(String label, List<R> records) {
        return records.isEmpty() ? new DataTree(label).rows(records) : of(label, (Class<R>) records.getFirst().getClass(), records);
    }

    /** A tree of {@code records} of {@code type}: its columns even when there are no rows (yet). */
    public static <R extends Record> DataTree of(String label, Class<R> type, List<R> records) {
        DataTree tree = new DataTree(label);
        {
            for (RecordComponent c : type.getRecordComponents()) {
                if (c.getName().equals("children") || c.getName().equals("hasChildren") || c.getName().equals("id")) continue;
                tree.column(c.getName(), Rows.words(c.getName()), DataTable.typeOf(c.getType()));
            }
        }
        return tree.rows(records);
    }

    public DataTree column(String key, String header, DataTable.ColumnType type) {
        Map<String, Object> column = new LinkedHashMap<>();
        column.put("key", key);
        column.put("header", header);
        column.put("type", type.name().toLowerCase(Locale.ROOT));
        if (type == DataTable.ColumnType.MONEY) column.put("currency", "@" + key + "Currency");
        columns.removeIf(c -> key.equals(c.get("key")));
        columns.add(column);
        return this;
    }

    public DataTree column(String key, String header) {
        return column(key, header, DataTable.ColumnType.TEXT);
    }

    /** The rows at the top; each one's children in its {@code children} list. */
    public DataTree rows(List<?> rows) {
        return prop("rows", Rows.rows(rows));
    }

    public DataTree rowKey(String key) { return prop("rowKey", key); }
    /** The rows open. */
    public DataTree expanded(List<String> ids) { return prop("expanded", List.copyOf(ids)); }
    public DataTree onExpandedChange(Consumer<List<String>> action) { return on("onExpandedChange", v -> action.accept(Rows.texts(v))); }
    /** A row with children not loaded yet ({@code hasChildren}) was opened: load them, the screen shows again. */
    public DataTree onExpand(Consumer<String> action) { return on("onExpand", v -> action.accept(String.valueOf(v))); }
    public DataTree selectable() { return prop("selectable", true); }
    public DataTree onRowOpen(Consumer<String> action) { return on("onRowOpen", v -> action.accept(String.valueOf(v))); }
    public DataTree menu(String label, Consumer<String> action) {
        return add(slot(new Element("menuItem").set("label", label).handle("onSelect", v -> action.accept(String.valueOf(v))), "menu"));
    }

    public List<Map<String, Object>> columns() {
        return List.copyOf(columns);
    }

    @Override
    DataTree rowsOf(List<?> rows) {
        return rows(rows);
    }
}
