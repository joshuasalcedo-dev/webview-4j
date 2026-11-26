package io.joshuasalcedo.webview4j.plugin.collection;

import java.util.Map;

/**
 * Where a page of rows is: the page (from 1), how many rows a page holds, and how many there are in all. A
 * collection works out its page count from it.
 */
public record Paging(int page, int size, long total) {

    public Paging {
        page = Math.max(1, page);
        size = Math.max(0, size);
        total = Math.max(0, total);
    }

    /** How many pages there are (1 when everything fits on one). */
    public int pages() {
        return size == 0 ? 1 : (int) Math.max(1, (total + size - 1) / size);
    }

    public boolean hasNext() {
        return page < pages();
    }

    public boolean hasPrevious() {
        return page > 1;
    }

    /** The paging of a query's answer. */
    public static Paging of(Query query, long total) {
        return new Paging(query.page(), query.size(), total);
    }

    Map<String, Object> write() {
        return Map.of("page", page, "size", size, "total", total);
    }
}
