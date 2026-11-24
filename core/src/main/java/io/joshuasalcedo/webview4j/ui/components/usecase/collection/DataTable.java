package io.joshuasalcedo.webview4j.ui.components.usecase.collection;

import io.joshuasalcedo.valueobject.financial.Money;
import io.joshuasalcedo.webview4j.ui.Component;
import io.joshuasalcedo.webview4j.ui.components.Element;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * A table of records, as the system draws one: sortable columns, a search box, rows chosen with check boxes, a
 * right-click menu, double-click to open, cells edited in place, and pages. The Java side of the kit's
 * {@code DataTable}.
 * <pre>{@code
 * record Order(String id, String customer, int items, Money total, LocalDate placed, boolean paid) {}
 *
 * DataTable.of("Orders", orders)             // a column per component, titled and typed from it
 *         .selectable().onSelectionChange(ids -> chosen = ids)
 *         .search().pageSize(20)
 *         .onRowOpen(id -> page.navigate("/orders/" + id))
 *         .rowMenu("Delete", id -> orders.delete(id))
 * }</pre>
 *
 * <p>Sorting, searching and paging happen in the page unless their handlers are given: then the server does them
 * (a {@code Pageable} call), and the table shows the rows it is given, one page of them ({@link #page}, {@link #total}).
 */
public final class DataTable extends Component<DataTable> {

    /** How a column's values are shown. */
    public enum ColumnType { TEXT, NUMBER, MONEY, DATE, DATETIME, BOOLEAN, PILL, LINK }

    /** How the rows are sorted: by {@code key}, descending or not. */
    public record Sort(String key, boolean descending) {}

    /** A cell edited in place: the row's id, the column, and what was typed. */
    public record CellEdit(String id, String key, String value) {}

    private final List<Map<String, Object>> columns = new ArrayList<>();

    public DataTable(String label) {
        super("dataTable");
        prop("label", label);
        prop("columns", columns);
    }

    /** A table of {@code records}, with a column for each of the record's components. */
    public static <R extends Record> DataTable of(String label, List<R> records) {
        DataTable table = new DataTable(label);
        if (!records.isEmpty()) {
            for (RecordComponent c : records.getFirst().getClass().getRecordComponents()) {
                table.column(c.getName(), Rows.words(c.getName()), typeOf(c.getType()));
            }
        }
        return table.rows(records);
    }

    // ---- columns ----

    /** A column showing the rows' {@code key} field. */
    public DataTable column(String key, String header, ColumnType type) {
        Map<String, Object> column = new LinkedHashMap<>();
        column.put("key", key);
        column.put("header", header);
        column.put("type", type.name().toLowerCase(Locale.ROOT));
        if (type == ColumnType.MONEY) column.put("currency", "@" + key + "Currency");
        columns.removeIf(c -> key.equals(c.get("key")));
        columns.add(column);
        return this;
    }

    public DataTable column(String key, String header) {
        return column(key, header, ColumnType.TEXT);
    }

    /** Money in one currency (ISO), its values in the currency's smallest unit. */
    public DataTable moneyColumn(String key, String header, String currency) {
        column(key, header, ColumnType.MONEY);
        set(key, "currency", currency);
        return this;
    }

    /** Edited in place with a double-click; {@link #onCellEdit} gets what was typed. */
    public DataTable editable(String key) {
        return set(key, "editable", true);
    }

    public DataTable unsortable(String key) {
        return set(key, "sortable", false);
    }

    /** A column's width, CSS pixels. */
    public DataTable columnWidth(String key, double width) {
        return set(key, "width", width);
    }

    /** A column's header. */
    public DataTable header(String key, String header) {
        return set(key, "header", header);
    }

    /** Leaves a column out. */
    public DataTable hide(String key) {
        columns.removeIf(c -> key.equals(c.get("key")));
        return this;
    }

    private DataTable set(String key, String name, Object value) {
        for (Map<String, Object> c : columns) if (key.equals(c.get("key"))) c.put(name, value);
        return this;
    }

    // ---- rows ----

    /**
     * The rows: records (by their components), maps, or any objects (by their getters are not read: give records or
     * maps). Money becomes its smallest unit and currency, temporal values their ISO text, enums their names, other
     * value objects their text.
     */
    public DataTable rows(List<?> rows) {
        return prop("rows", Rows.rows(rows));
    }

    /** The field that tells rows apart: "id" unless set. */
    public DataTable rowKey(String key) {
        return prop("rowKey", key);
    }

    static ColumnType typeOf(Class<?> type) {
        if (type == Money.class) return ColumnType.MONEY;
        if (type == boolean.class || type == Boolean.class) return ColumnType.BOOLEAN;
        if (type.isPrimitive() || Number.class.isAssignableFrom(type)) return ColumnType.NUMBER;
        if (type == java.time.LocalDate.class || type.getSimpleName().equals("CalendarDate")) return ColumnType.DATE;
        if (Temporal.class.isAssignableFrom(type)) return ColumnType.DATETIME;
        if (type.isEnum()) return ColumnType.PILL;
        return ColumnType.TEXT;
    }

    // ---- choosing rows ----

    /** Check boxes to choose rows. */
    public DataTable selectable() {
        return prop("selectable", true);
    }

    public DataTable selected(List<String> ids) {
        return prop("selected", List.copyOf(ids));
    }

    public DataTable onSelectionChange(Consumer<List<String>> action) {
        return on("onSelectionChange", v -> action.accept(Rows.texts(v)));
    }

    /** A button above the table acting on the chosen rows; call again for each. */
    public DataTable action(String label, Consumer<List<String>> action) {
        Element button = new Element("tableAction").set("label", label).handle("onSelect", v -> action.accept(Rows.texts(v)));
        return add(slot(button, "action"));
    }

    // ---- sorting, searching, pages ----

    /** The sort shown. With {@link #onSortChange}, sorting is the server's. */
    public DataTable sort(String key, boolean descending) {
        return prop("sort", Map.of("key", key, "desc", descending));
    }

    /** Sorting is the server's: the new sort (null for none); the rows come back sorted. */
    public DataTable onSortChange(Consumer<Sort> action) {
        return on("onSortChange", v -> action.accept(v instanceof Map<?, ?> m && m.get("key") != null
                ? new Sort(String.valueOf(m.get("key")), Boolean.TRUE.equals(m.get("desc"))) : null));
    }

    /** A search box above the table. */
    public DataTable search() {
        return prop("search", true);
    }

    /** What the search box holds. With {@link #onFilterChange}, searching is the server's. */
    public DataTable filter(String text) {
        return prop("filter", text);
    }

    public DataTable onFilterChange(Consumer<String> action) {
        return on("onFilterChange", v -> action.accept(v == null ? "" : String.valueOf(v)));
    }

    /** Rows per page; 0 for no pages. */
    public DataTable pageSize(int size) {
        return prop("pageSize", size);
    }

    /** The page shown (from 1). With {@link #total} and {@link #onPageChange}, paging is the server's. */
    public DataTable page(int page) {
        return prop("page", page);
    }

    /** How many rows there are in all, on the server. */
    public DataTable total(long total) {
        return prop("total", total);
    }

    public DataTable onPageChange(IntConsumer action) {
        return on("onPageChange", v -> action.accept(v instanceof Number n ? n.intValue() : 1));
    }

    // ---- acting on rows ----

    /** A row opened (double-click, Enter): its id. */
    public DataTable onRowOpen(Consumer<String> action) {
        return on("onRowOpen", v -> action.accept(String.valueOf(v)));
    }

    /** An entry of every row's right-click menu: it gets the row's id. Call again for each. */
    public DataTable rowMenu(String label, Consumer<String> action) {
        Element entry = new Element("rowMenuItem").set("label", label).handle("onSelect", v -> action.accept(String.valueOf(v)));
        return add(slot(entry, "rowMenu"));
    }

    /** A cell edited in place ({@link #editable}). */
    public DataTable onCellEdit(Consumer<CellEdit> action) {
        return on("onCellEdit", v -> {
            Map<?, ?> m = v instanceof Map<?, ?> map ? map : Map.of();
            action.accept(new CellEdit(String.valueOf(m.get("id")), String.valueOf(m.get("key")), m.get("value") == null ? "" : String.valueOf(m.get("value"))));
        });
    }

    public DataTable loading(boolean loading) {
        return prop("loading", loading);
    }

    /** Said when there are no rows. */
    public DataTable empty(String text) {
        return prop("empty", text);
    }

    /** The columns, for tests and tools. */
    public List<Map<String, Object>> columns() {
        return List.copyOf(columns);
    }

    @Override
    public String toString() {
        return "DataTable" + Objects.toString(get("label"));
    }
}
