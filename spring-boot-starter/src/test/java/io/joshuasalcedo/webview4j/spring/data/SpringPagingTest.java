package io.joshuasalcedo.webview4j.spring.data;

import static org.junit.jupiter.api.Assertions.*;

import io.joshuasalcedo.webview4j.ui.components.usecase.collection.Paging;
import io.joshuasalcedo.webview4j.ui.components.usecase.collection.Query;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class SpringPagingTest {

    @Test
    void aQueryIsAPageableCountingFromZero() {
        Query query = new Query(3, 20, List.of(Query.Order.desc("placed"), Query.Order.asc("customer")), "ana", Map.of());
        Pageable pageable = SpringPaging.pageable(query);
        assertEquals(2, pageable.getPageNumber());
        assertEquals(20, pageable.getPageSize());
        assertEquals(Sort.by(Sort.Order.desc("placed"), Sort.Order.asc("customer")), pageable.getSort());
        assertTrue(SpringPaging.pageable(Query.first(0)).isUnpaged(), "size 0: everything");
    }

    @Test
    void aPageIsRowsAndPagingCountingFromOne() {
        var page = new PageImpl<>(List.of("a", "b"), PageRequest.of(1, 2), 7);
        Query.Result<String> result = SpringPaging.result(page);
        assertEquals(List.of("a", "b"), result.rows());
        assertEquals(new Paging(2, 2, 7), result.paging());
        assertEquals(4, result.paging().pages());
    }

    @Test
    void aPageableIsWhereAQueryStarts() {
        Query query = SpringPaging.query(PageRequest.of(0, 50, Sort.by("name")));
        assertEquals(new Query(1, 50, List.of(Query.Order.asc("name")), "", Map.of()), query);
    }
}
