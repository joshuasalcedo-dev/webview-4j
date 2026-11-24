package io.joshuasalcedo.webview4j.ui.components.usecase.collection;

import static org.junit.jupiter.api.Assertions.*;

import io.joshuasalcedo.webview4j.ui.components.usecase.collection.Query.Filter;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The query model: answered in memory, and the same after a trip to the page and back. */
class QueryTest {

    enum Status { NEW, PAID, SHIPPED }

    record Order(String id, String customer, int items, Status status, LocalDate placed, boolean rush) {}

    static final List<Order> ORDERS = List.of(
            new Order("1", "Ana Reyes", 3, Status.PAID, LocalDate.of(2026, 1, 5), false),
            new Order("2", "Grace Hopper", 1, Status.NEW, LocalDate.of(2026, 2, 9), true),
            new Order("3", "Ada Lovelace", 7, Status.PAID, LocalDate.of(2026, 3, 1), true),
            new Order("4", "Marco Silva", 2, Status.SHIPPED, LocalDate.of(2026, 3, 20), false),
            new Order("5", "Kim Park", 5, Status.PAID, LocalDate.of(2026, 4, 2), false));

    @Test
    void pagesCountFromOne() {
        var result = Query.first(2).withPage(3).apply(ORDERS);
        assertEquals(List.of("5"), result.rows().stream().map(Order::id).toList());
        assertEquals(new Paging(3, 2, 5), result.paging());
        assertEquals(3, result.paging().pages());
        assertFalse(result.paging().hasNext());
        assertEquals(4, Query.first(2).withPage(3).offset());
    }

    @Test
    void filtersOfEachKind() {
        Query query = Query.first(0)
                .withFilter("status", new Filter.Options(List.of("PAID")))
                .withFilter("items", new Filter.Range(3, null))
                .withFilter("placed", new Filter.Range("2026-02-01", "2026-12-31"))
                .withFilter("rush", new Filter.Bool(true));
        assertEquals(List.of("3"), query.apply(ORDERS).rows().stream().map(Order::id).toList());
        assertEquals(List.of("2"), Query.first(0).withFilter("customer", new Filter.Text("HOPP")).apply(ORDERS).rows().stream().map(Order::id).toList());
        assertTrue(query.filtering());
    }

    @Test
    void sortsBySeveralAndSearches() {
        var sorted = Query.first(0).withSort(Query.Order.asc("status"), Query.Order.desc("items")).apply(ORDERS).rows();
        assertEquals(List.of("2", "3", "5", "1", "4"), sorted.stream().map(Order::id).toList());
        assertEquals(List.of("4"), Query.first(0).withSearch("marco").apply(ORDERS).rows().stream().map(Order::id).toList());
    }

    @Test
    void theSameAfterATripToThePage() {
        Query query = new Query(2, 25, List.of(Query.Order.desc("placed")), "ana",
                Map.of("status", new Filter.Options(List.of("PAID", "NEW")), "items", new Filter.Range(1L, 10L),
                        "rush", new Filter.Bool(false), "customer", new Filter.Text("re")));
        assertEquals(query, Query.read(query.write()));
    }
}
