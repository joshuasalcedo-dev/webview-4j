package io.joshuasalcedo.webview4j.spring.data;

import io.joshuasalcedo.webview4j.ui.components.usecase.collection.Paging;
import io.joshuasalcedo.webview4j.ui.components.usecase.collection.Query;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * A collection's {@link Query} and Spring Data's paging, both ways: a query as a {@link Pageable} (its page, size and
 * order) for a repository or a paged endpoint, and a {@link Page} as the rows and the {@link Paging} to show.
 * <pre>{@code
 * Page<Order> page = orders.findAll(SpringPaging.pageable(query));
 * DataTable.of("Orders", page.getContent()).page(SpringPaging.result(page))
 *         .onQueryChange(q -> query = q)
 * }</pre>
 * The query's search and filters are not paging: a repository reads them from the query
 * ({@code query.search()}, {@code query.filters()}), in a Specification or a query method of its own.
 *
 * <p>Needs {@code spring-data-commons} on the class path (any Spring Data module brings it).
 */
public final class SpringPaging {

    private SpringPaging() {}

    /** The query's page, size and order, as Spring Data's (whose pages count from 0). Size 0 is unpaged. */
    public static Pageable pageable(Query query) {
        Sort sort = sort(query);
        if (query.size() == 0) return sort.isSorted() ? Pageable.unpaged(sort) : Pageable.unpaged();
        return PageRequest.of(query.page() - 1, query.size(), sort);
    }

    /** The query's order as Spring Data's. */
    public static Sort sort(Query query) {
        List<Sort.Order> orders = new ArrayList<>();
        for (Query.Order o : query.sort()) orders.add(o.descending() ? Sort.Order.desc(o.key()) : Sort.Order.asc(o.key()));
        return Sort.by(orders);
    }

    /** Where a Spring Data page is, counting from 1. */
    public static Paging paging(Page<?> page) {
        return new Paging(page.getNumber() + 1, page.getSize(), page.getTotalElements());
    }

    /** A Spring Data page as the rows and the paging a collection shows ({@code table.page(result)}). */
    public static <T> Query.Result<T> result(Page<T> page) {
        return new Query.Result<>(page.getContent(), paging(page));
    }

    /** The query a collection starts with for this pageable: its page, size and order. */
    public static Query query(Pageable pageable) {
        List<Query.Order> orders = new ArrayList<>();
        for (Sort.Order o : pageable.getSort()) orders.add(new Query.Order(o.getProperty(), o.isDescending()));
        return pageable.isPaged()
                ? new Query(pageable.getPageNumber() + 1, pageable.getPageSize(), orders, "", java.util.Map.of())
                : new Query(1, 0, orders, "", java.util.Map.of());
    }
}
