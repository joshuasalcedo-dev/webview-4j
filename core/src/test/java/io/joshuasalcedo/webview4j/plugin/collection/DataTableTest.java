package io.joshuasalcedo.webview4j.plugin.collection;

import static io.joshuasalcedo.webview4j.ui.Ui.*;
import static org.junit.jupiter.api.Assertions.*;

import io.joshuasalcedo.valueobject.financial.FiatCurrency;
import io.joshuasalcedo.valueobject.financial.Money;
import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.View;
import io.joshuasalcedo.webview4j.ui.Component;
import io.joshuasalcedo.webview4j.ui.Screen;
import io.joshuasalcedo.webview4j.ui.Ui;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** A data table from Java records: its columns from the record, and what the person does back in Java. */
class DataTableTest {

    enum Status { NEW, SHIPPED }

    record Order(String id, String customer, int items, Money total, LocalDate placed, boolean paid, Status status) {}

    static final List<Order> ORDERS = List.of(
            new Order("A-1", "Ada", 2, Money.of(new BigDecimal("499.00"), FiatCurrency.PHP), LocalDate.of(2026, 11, 1), true, Status.NEW),
            new Order("A-2", "Grace", 5, Money.of(new BigDecimal("12.50"), FiatCurrency.USD), LocalDate.of(2026, 11, 2), false, Status.SHIPPED));

    @Test
    void aRecordsComponentsAreItsColumns() {
        DataTable table = DataTable.of("Orders", ORDERS);
        List<Map<String, Object>> columns = table.columns();
        assertEquals(List.of("id", "customer", "items", "total", "placed", "paid", "status"), columns.stream().map(c -> c.get("key")).toList());
        assertEquals(List.of("text", "text", "number", "money", "date", "boolean", "pill"), columns.stream().map(c -> c.get("type")).toList());
        assertEquals("Customer", columns.get(1).get("header"));
        assertEquals("@totalCurrency", columns.get(3).get("currency"), "money keeps its currency per row");
    }

    static final class Orders implements Screen {
        List<String> chosen = List.of();
        DataTable.Sort sort;
        String opened = "";
        final List<String> log = new ArrayList<>();

        @Override
        public Component<?> render() {
            List<Order> rows = new ArrayList<>(ORDERS);
            if (sort != null && sort.key().equals("customer") && sort.descending()) rows = rows.reversed();
            return column(
                    DataTable.of("Orders", rows).selectable().selected(chosen).onSelectionChange(ids -> chosen = ids)
                            .sort(sort == null ? "id" : sort.key(), sort != null && sort.descending())
                            .onSortChange(s -> sort = s)
                            .onRowOpen(id -> opened = id),
                    text("chosen " + chosen + " opened " + opened + " sort " + (sort == null ? "-" : sort.key() + (sort.descending() ? " desc" : " asc"))))
                    .padding(16);
        }
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    @Timeout(90)
    void whatThePersonDoesInTheTableReachesJava() throws Exception {
        try (App app = App.start()) {
            Orders screen = new Orders();
            View view = Ui.open(app, screen, o -> o.headless(true).size(1000, 600)).join().view();
            String shown = until(view, "Grace");
            assertTrue(shown.contains("₱499.00") || shown.contains("PHP"), "money in its currency's way: " + shown);

            // a row's check box
            view.eval("[...document.querySelectorAll('tbody tr')].find(r => r.innerText.includes('Grace')).querySelector('input[type=checkbox]').click()").join();
            until(view, "chosen [A-2]");
            assertEquals(List.of("A-2"), screen.chosen);

            // a column's sort, the server's: Java gets a typed Sort
            view.eval("[...document.querySelectorAll('th button')].find(b => b.innerText.includes('Customer')).click()").join();
            until(view, "sort customer");
            assertEquals("customer", screen.sort.key());

            // a row opened
            view.eval("[...document.querySelectorAll('tbody tr')].find(r => r.innerText.includes('Ada')).dispatchEvent(new MouseEvent('dblclick', {bubbles: true}))").join();
            until(view, "opened A-1");
            assertEquals("A-1", screen.opened);
        }
    }

    static String until(View view, String part) throws InterruptedException {
        String text = "";
        for (int i = 0; i < 150; i++) {
            text = String.valueOf(view.eval("document.body.innerText").join());
            if (text.contains(part)) return text;
            Thread.sleep(100);
        }
        throw new AssertionError("never showed '" + part + "': " + text);
    }
}
