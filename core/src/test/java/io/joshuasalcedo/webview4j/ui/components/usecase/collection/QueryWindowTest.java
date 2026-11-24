package io.joshuasalcedo.webview4j.ui.components.usecase.collection;

import static io.joshuasalcedo.webview4j.ui.Ui.*;
import static org.junit.jupiter.api.Assertions.*;

import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.View;
import io.joshuasalcedo.webview4j.ui.Component;
import io.joshuasalcedo.webview4j.ui.Screen;
import io.joshuasalcedo.webview4j.ui.Ui;
import io.joshuasalcedo.webview4j.ui.UiWindow;
import io.joshuasalcedo.webview4j.ui.components.usecase.collection.Query.Filter;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** A table whose server answers the query (pages, column filters), and its states: loading, error, empty. */
@EnabledOnOs(OS.WINDOWS)
class QueryWindowTest {

    record Order(String id, String customer, int items, QueryTest.Status status, boolean rush) {}

    static final class Orders implements Screen {
        final List<Order> all = new ArrayList<>();
        Query query = Query.first(5);
        final List<Query> asked = new ArrayList<>();
        boolean loading;
        boolean failing;
        boolean none;
        boolean created;

        Orders() {
            QueryTest.Status[] statuses = QueryTest.Status.values();
            for (int i = 1; i <= 23; i++) all.add(new Order(String.valueOf(i), "Customer " + i, i % 7 + 1, statuses[i % 3], i % 4 == 0));
        }

        @Override
        public Component<?> render() {
            var result = query.apply(none ? List.<Order>of() : all);
            DataTable table = DataTable.of("Orders", all).result(result).query(query)
                    .filterable("customer").filterable("items").filterable("status", QueryTest.Status.class).filterable("rush")
                    .onQueryChange(q -> {
                        asked.add(q);
                        query = q;
                    })
                    .loading(loading)
                    .empty("No orders yet", "Orders you take show here.").emptyAction("New order", () -> created = true)
                    .onRetry(() -> failing = false);
            if (failing) table.error("The orders could not be loaded", "The server did not answer.");
            return column(table, text("asked " + asked.size() + (created ? " created" : ""))).padding(16).grow();
        }
    }

    @Test
    @Timeout(120)
    void theServerAnswersPagesAndFiltersAndTheStatesShow() throws Exception {
        try (App app = App.start()) {
            Orders screen = new Orders();
            UiWindow window = Ui.open(app, screen, o -> o.headless(true).size(1000, 800)).join();
            View view = window.view();
            String text = CollectionsTest.until(view, "Page 1 of 5");
            assertTrue(text.contains("23 in all"), text);

            // a page from the server
            click(view, "Next");
            CollectionsTest.until(view, "Page 2 of 5");
            assertEquals(2, screen.query.page());
            assertTrue(String.valueOf(view.eval("document.body.innerText").join()).contains("Customer 6"));

            // a column filter: one of the enum's values; the query starts again at page 1
            click(view, "Filters");
            Thread.sleep(200);
            view.eval("""
                    (() => { const s = document.querySelector('select[aria-label="Filter Status"]');
                      const set = Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype, 'value').set;
                      set.call(s, 'PAID'); s.dispatchEvent(new Event('change', {bubbles: true})); })()
                    """).join();
            CollectionsTest.until(view, "Page 1 of 2");
            assertEquals(new Filter.Options(List.of("PAID")), screen.query.filters().get("status"));
            assertEquals(1, screen.query.page());

            // a filter that hides everything: No matches, and Clear filters
            view.eval("""
                    (() => { const i = document.querySelector('input[aria-label="Filter Customer"]');
                      const set = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set;
                      set.call(i, 'nobody'); i.dispatchEvent(new Event('input', {bubbles: true})); })()
                    """).join();
            CollectionsTest.until(view, "No matches");
            click(view, "Clear filters");
            CollectionsTest.until(view, "Page 1 of 5");
            assertTrue(screen.query.filters().isEmpty());

            // loading again: the rows stay, a bar runs
            screen.loading = true;
            window.refresh();
            Thread.sleep(500);
            assertEquals(true, view.eval("!!document.querySelector('.nt-loading-bar') && document.querySelectorAll('tbody tr').length > 0").join());
            screen.loading = false;

            // an error, and Try again
            screen.failing = true;
            window.refresh();
            CollectionsTest.until(view, "The orders could not be loaded");
            click(view, "Try again");
            CollectionsTest.until(view, "Page 1 of 5");

            // nothing at all: the empty state and its action
            screen.none = true;
            window.refresh();
            CollectionsTest.until(view, "No orders yet");
            click(view, "New order");
            CollectionsTest.until(view, "created");
        }
    }

    static void click(View view, String label) {
        view.eval("[...document.querySelectorAll('button')].find(b => b.innerText.trim() === '" + label + "').click()").join();
    }
}
