package io.joshuasalcedo.webview4j.ui.components.egui;

import static org.junit.jupiter.api.Assertions.*;

import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.View;
import io.joshuasalcedo.webview4j.WindowOptions;
import io.joshuasalcedo.webview4j.ui.Window;
import io.joshuasalcedo.webview4j.ui.components.Text;
import io.joshuasalcedo.webview4j.ui.components.TextVariant;
import io.joshuasalcedo.webview4j.ui.components.layout.HBox;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** egui-4j's Rust components embedded in a Java screen: drawn in the page, fed from Java, their events back in Java. */
@EnabledOnOs(OS.WINDOWS)
class EguiWindowTest {

    @Test
    @Timeout(90)
    void aChartAndATableFromRustSitInAJavaScreen() throws Exception {
        LineChart chart = new LineChart("CPU and memory").xLabel("seconds").yLabel("%").maxPoints(200).height(260);
        chart.series("CPU", 0x0A84FF).series("Memory", 0x30D158);
        LiveCsv table = new LiveCsv("Customers imported").columns("id", "name", "country").height(220);
        Text said = new Text("Click the chart");
        AtomicReference<double[]> clicked = new AtomicReference<>();
        chart.onClick((x, y) -> {
            clicked.set(new double[] {x, y});
            said.setText("Clicked at %.1f s, %.0f %%".formatted(x, y));
        });
        String out = System.getProperty("demo.out");
        Window window = new Window("egui in Java") {
            {
                setSize(820, 680);
                add(new Text("Rust (egui) inside a Java screen").variant(TextVariant.TITLE), chart, table, new HBox(8, said));
            }

            @Override
            protected WindowOptions options(WindowOptions o) {
                return super.options(o).headless(out == null);
            }
        };

        try (App app = App.start()) {
            View view = window.open(app).join().view();
            // fed from another thread while it shows
            Thread feeder = Thread.ofPlatform().start(() -> {
                for (int i = 0; i < 120; i++) {
                    chart.append("CPU", i * 0.5, 40 + 25 * Math.sin(i / 8.0));
                    chart.append("Memory", i * 0.5, 55 + 10 * Math.cos(i / 15.0));
                }
                for (int i = 1; i <= 40; i++) table.addRow(i, "Customer " + i, i % 3 == 0 ? "PH" : i % 3 == 1 ? "FR" : "US");
                table.done();
            });
            feeder.join();
            assertEquals(120, chart.pointCount("CPU"));
            assertEquals(40, table.getRowCount());

            // both canvases mounted, no error in their place
            String canvases = "document.querySelectorAll('canvas').length";
            for (int i = 0; i < 150 && !"2".equals(String.valueOf(view.eval(canvases).join()).replace(".0", "")); i++) Thread.sleep(100);
            assertEquals("2", String.valueOf(view.eval(canvases).join()).replace(".0", ""), "two egui canvases");
            Thread.sleep(1500);   // their first frames, with the data
            Object text = view.eval("document.body.innerText").join();
            assertFalse(String.valueOf(text).toLowerCase().contains("failed"), "no component failed: " + text);

            // a click on the chart comes back to Java as the value under the pointer
            view.eval("""
                    (() => { const c = document.querySelectorAll('canvas')[0]; const r = c.getBoundingClientRect();
                      const at = {clientX: r.left + r.width / 2, clientY: r.top + r.height / 2, bubbles: true, pointerId: 1, button: 0, buttons: 1, pointerType: 'mouse', isPrimary: true};
                      c.dispatchEvent(new PointerEvent('pointermove', at)); c.dispatchEvent(new PointerEvent('pointerdown', at));
                      c.dispatchEvent(new PointerEvent('pointerup', {...at, buttons: 0})); })()
                    """).join();
            for (int i = 0; i < 50 && clicked.get() == null; i++) Thread.sleep(100);
            assertNotNull(clicked.get(), "the chart's click reached Java");

            if (out != null) {
                Thread.sleep(500);
                view.screenshot(Path.of(out).resolve("egui-in-java.png")).join();
            }
        }
    }
}
