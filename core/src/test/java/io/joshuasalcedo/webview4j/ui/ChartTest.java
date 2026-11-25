package io.joshuasalcedo.webview4j.ui;

import static io.joshuasalcedo.webview4j.ui.Ui.*;
import static org.junit.jupiter.api.Assertions.*;

import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.LogRecord;
import io.joshuasalcedo.webview4j.View;
import io.joshuasalcedo.webview4j.ui.components.Chart;
import io.joshuasalcedo.webview4j.ui.components.ChartSeries;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** A chart from Java: Recharts loads the first time one is shown, draws its marks, and has its table view. */
@EnabledOnOs(OS.WINDOWS)
class ChartTest {

    /** The app's own rows: any record; the chart reads the fields its x and series name. */
    record Visits(String month, int desktop, int mobile) {}

    static final class Charts implements Screen {
        @Override
        public Component<?> render() {
            return column(new Chart(Chart.Kind.BAR,
                    List.of(new Visits("Jan", 186, 80), new Visits("Feb", 305, 200), new Visits("Mar", 237, 120)), "month",
                    List.of(new ChartSeries("desktop", "Desktop"), new ChartSeries("mobile", "Mobile"))).title("Visitors")).padding(16);
        }
    }

    @Test
    @Timeout(90)
    void aChartLoadsDrawsItsBarsAndHasATable() throws Exception {
        try (App app = App.start()) {
            List<String> errors = new CopyOnWriteArrayList<>();
            app.subscribe(LogRecord.class, log -> {
                if (log.level() == LogRecord.Level.ERROR) errors.add(log.message());
            });
            View view = Ui.open(app, new Charts(), o -> o.headless(true).size(800, 500)).join().view();
            Object bars = 0;
            for (int i = 0; i < 100; i++) {
                bars = view.eval("document.querySelectorAll('.recharts-bar-rectangle').length").join();
                if (((Number) bars).intValue() > 0) break;
                Thread.sleep(100);
            }
            assertEquals(6, ((Number) bars).intValue(), "3 months × 2 series of bars; page errors: " + errors);
            assertTrue(String.valueOf(view.eval("document.body.innerText").join()).contains("Desktop"), "a legend for two series");

            view.eval("[...document.querySelectorAll('.nt-chart-view button')].find(b => b.textContent === 'Table').click()").join();
            Thread.sleep(300);
            assertEquals(3, ((Number) view.eval("document.querySelectorAll('.nt-chart-table tbody tr').length").join()).intValue(),
                    "the same numbers as a table");
        }
    }
}
