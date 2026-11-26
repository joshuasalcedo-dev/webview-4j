package io.joshuasalcedo.webview4j.plugin.collection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * What a person asks of a collection, as one value: which page and how big, the order, the search box's text, and
 * each column's filter. A collection with {@code onQueryChange} sends it whenever it changes; the server answers it
 * (a database query, a {@code Pageable} call) and the collection shows that page, with {@link Paging} saying where it
 * is.
 * <pre>{@code
 * DataTable.of("Orders", result.rows()).paging(result.paging())
 *         .filterable("status").filterable("total").filterable("placed")
 *         .onQueryChange(query -> result = orders.find(query))
 * }</pre>
 *
 * @param page    from 1
 * @param size    rows on a page; 0 for all of them
 * @param sort    the orders, the first sorting first
 * @param search  the search box's text ("" for none)
 * @param filters each filtered column's filter, by its key
 */
public record Query(int page, int size, List<Order> sort, String search, Map<String, Filter> filters) {

    /** One order: by {@code key}, descending or not. */
    public record Order(String key, boolean descending) {
        public static Order asc(String key) { return new Order(key, false); }
        public static Order desc(String key) { return new Order(key, true); }
    }

    /** A column's filter. */
    public sealed interface Filter {
        /** The value's text contains this, in any case. */
        record Text(String text) implements Filter {}
        /** The value is one of these (an enum's names, a status). */
        record Options(List<String> values) implements Filter {}
        /** Between {@code from} and {@code to}, either one open (null): numbers, or ISO dates as text. */
        record Range(Object from, Object to) implements Filter {}
        /** Yes or no. */
        record Bool(boolean value) implements Filter {}
    }

    public Query {
        page = Math.max(1, page);
        size = Math.max(0, size);
        sort = List.copyOf(sort == null ? List.of() : sort);
        search = search == null ? "" : search;
        filters = Map.copyOf(filters == null ? Map.of() : filters);
    }

    /** The first page of {@code size}, nothing else asked. */
    public static Query first(int size) {
        return new Query(1, size, List.of(), "", Map.of());
    }

    public Query withPage(int page) { return new Query(page, size, sort, search, filters); }
    public Query withSize(int size) { return new Query(1, size, sort, search, filters); }
    public Query withSort(Order... orders) { return new Query(1, size, List.of(orders), search, filters); }
    public Query withSearch(String text) { return new Query(1, size, sort, text, filters); }

    public Query withFilter(String key, Filter filter) {
        Map<String, Filter> next = new LinkedHashMap<>(filters);
        if (filter == null) next.remove(key); else next.put(key, filter);
        return new Query(1, size, sort, search, next);
    }

    /** The rows skipped before this page: for a database's {@code OFFSET}. */
    public long offset() {
        return size == 0 ? 0 : (long) (page - 1) * size;
    }

    /** Whether the search or any filter asks for something. */
    public boolean filtering() {
        return !search.isBlank() || !filters.isEmpty();
    }

    /**
     * This query answered in memory, for rows that are all at hand (a list, a small table): filtered, searched in
     * the given fields, sorted, and cut to this page. {@code field} reads a row's field by its key.
     */
    public <T> Result<T> apply(List<T> rows, Function<T, Map<String, Object>> fields) {
        List<T> out = new ArrayList<>();
        for (T row : rows) {
            Map<String, Object> f = fields.apply(row);
            if (passes(f)) out.add(row);
        }
        if (!sort.isEmpty()) {
            Comparator<T> by = null;
            for (Order o : sort) {
                Comparator<T> one = Comparator.comparing(r -> (Object) fields.apply(r).get(o.key()), Query::compare);
                if (o.descending()) one = one.reversed();
                by = by == null ? one : by.thenComparing(one);
            }
            out.sort(by);
        }
        long total = out.size();
        List<T> page = size == 0 ? out : out.subList((int) Math.min(offset(), total), (int) Math.min(offset() + size, total));
        return new Result<>(List.copyOf(page), new Paging(this.page, size, total));
    }

    /** The rows of a record type answered in memory ({@link #apply(List, Function)} with the record's components). */
    public <T> Result<T> apply(List<T> rows) {
        return apply(rows, Rows::row);
    }

    /** One page of rows and where it is. */
    public record Result<T>(List<T> rows, Paging paging) {}

    private boolean passes(Map<String, Object> fields) {
        for (var e : filters.entrySet()) if (!matches(fields.get(e.getKey()), e.getValue())) return false;
        if (search.isBlank()) return true;
        String needle = search.trim().toLowerCase(Locale.ROOT);
        for (Object v : fields.values()) if (v != null && v.toString().toLowerCase(Locale.ROOT).contains(needle)) return true;
        return false;
    }

    /** Whether a value passes a filter. */
    public static boolean matches(Object value, Filter filter) {
        return switch (filter) {
            case Filter.Text t -> value != null && value.toString().toLowerCase(Locale.ROOT).contains(t.text().trim().toLowerCase(Locale.ROOT));
            case Filter.Options o -> o.values().contains(String.valueOf(value));
            case Filter.Bool b -> Boolean.valueOf(b.value()).equals(value instanceof Boolean x ? x : Boolean.parseBoolean(String.valueOf(value)));
            case Filter.Range r -> (r.from() == null || compare(value, r.from()) >= 0) && (r.to() == null || compare(value, r.to()) <= 0);
        };
    }

    private static int compare(Object a, Object b) {
        if (a == null || b == null) return a == null ? (b == null ? 0 : -1) : 1;
        if (a instanceof Number x && b instanceof Number y) return Double.compare(x.doubleValue(), y.doubleValue());
        if (a instanceof Number x) {
            try { return Double.compare(x.doubleValue(), Double.parseDouble(b.toString())); } catch (NumberFormatException e) { /* as text */ }
        }
        return a.toString().compareTo(b.toString());
    }

    // ---- to and from the page ----

    static Query read(Object value) {
        if (!(value instanceof Map<?, ?> m)) return first(0);
        List<Order> sort = new ArrayList<>();
        if (m.get("sort") instanceof List<?> list) {
            for (Object o : list) if (o instanceof Map<?, ?> s) sort.add(new Order(String.valueOf(s.get("key")), Boolean.TRUE.equals(s.get("desc"))));
        }
        Map<String, Filter> filters = new LinkedHashMap<>();
        if (m.get("filters") instanceof Map<?, ?> fs) {
            fs.forEach((k, v) -> {
                Filter f = filter(v);
                if (f != null) filters.put(String.valueOf(k), f);
            });
        }
        return new Query(m.get("page") instanceof Number n ? n.intValue() : 1, m.get("size") instanceof Number n ? n.intValue() : 0,
                sort, m.get("search") == null ? "" : String.valueOf(m.get("search")), filters);
    }

    private static Filter filter(Object v) {
        if (!(v instanceof Map<?, ?> f)) return null;
        return switch (String.valueOf(f.get("kind"))) {
            case "text" -> new Filter.Text(String.valueOf(f.get("text")));
            case "options" -> new Filter.Options(Rows.texts(f.get("values")));
            case "range" -> new Filter.Range(f.get("from"), f.get("to"));
            case "boolean" -> new Filter.Bool(Boolean.TRUE.equals(f.get("value")));
            default -> null;
        };
    }

    Map<String, Object> write() {
        List<Object> orders = new ArrayList<>();
        for (Order o : sort) orders.add(Map.of("key", o.key(), "desc", o.descending()));
        Map<String, Object> fs = new LinkedHashMap<>();
        filters.forEach((k, f) -> fs.put(k, switch (f) {
            case Filter.Text t -> Map.of("kind", "text", "text", t.text());
            case Filter.Options o -> Map.of("kind", "options", "values", o.values());
            case Filter.Bool b -> Map.of("kind", "boolean", "value", b.value());
            case Filter.Range r -> {
                Map<String, Object> range = new LinkedHashMap<>();
                range.put("kind", "range");
                if (r.from() != null) range.put("from", r.from());
                if (r.to() != null) range.put("to", r.to());
                yield range;
            }
        }));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("page", page);
        out.put("size", size);
        out.put("sort", orders);
        out.put("search", search);
        out.put("filters", fs);
        return out;
    }

    @Override
    public String toString() {
        return "page " + page + " of " + size + (sort.isEmpty() ? "" : ", sort " + sort) + (search.isEmpty() ? "" : ", search '" + search + "'")
                + (filters.isEmpty() ? "" : ", filters " + filters);
    }
}
