package io.joshuasalcedo.swaswa.ffi;

import static io.joshuasalcedo.swaswa.ffi.EguiTest.widget;
import static io.joshuasalcedo.swaswa.ffi.ViewMethodsTest.until;
import static org.junit.jupiter.api.Assertions.*;

import io.joshuasalcedo.swaswa.html.Div;
import io.joshuasalcedo.swaswa.html.Document;
import io.joshuasalcedo.swaswa.html.Html;
import io.joshuasalcedo.swaswa.html.ImmediatePanel;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** egui inside a webview's window: a native panel over the page, where Java (or its element) puts it. */
@EnabledOnOs(OS.WINDOWS)
@Timeout(120)
class EguiPanelTest {

    static final long RED = 0x1E1EDCL;   // rgb(220,30,30) as 0x00BBGGRR

    static View redPage(App app, String title) {
        return app.open(WindowOptions.html("<body style='margin:0;background:rgb(220,30,30)'>").title(title).size(700, 500).position(200, 120)).join();
    }

    /** The colour on the screen at (x, y) in the view's page (CSS pixels). */
    static long at(View view, double x, double y) {
        Map<?, ?> c = (Map<?, ?>) view.client().join();
        double scale = ((Number) c.get("scale")).doubleValue();
        return Win32.pixelOnScreen((int) (((Number) c.get("x")).doubleValue() + x * scale), (int) (((Number) c.get("y")).doubleValue() + y * scale));
    }

    static boolean red(long c) {
        return Win32.near(c, RED);
    }

    @Test
    void anEguiPanelIsInItsWindowWherePutAndMoves() throws Exception {
        try (App app = App.start()) {
            View host = redPage(app, "swaswa test: egui panel");
            ImmediateWindow panel = Egui.on(app).embed(host, 20, 40, 300, 200, ui -> ui.label("in a page"));
            assertNotNull(widget(panel, "in a page"), "egui draws in it");
            ViewMethodsTest.front(host);
            assertFalse(red(until(() -> at(host, 170, 140), c -> !red(c))), "the panel shows where it was put");
            assertTrue(red(until(() -> at(host, 500, 400), EguiPanelTest::red)), "the page around it");

            panel.place(360, 40, 300, 200);
            assertTrue(red(until(() -> at(host, 170, 140), EguiPanelTest::red)), "moved: the page where it was");
            assertFalse(red(until(() -> at(host, 510, 140), c -> !red(c))), "and the panel where it went");

            panel.place(0, 0, 0, 0);
            assertTrue(red(until(() -> at(host, 510, 140), EguiPanelTest::red)), "no size: hidden");

            host.close().join();
            panel.whenClosed().get(10, TimeUnit.SECONDS);   // it went with its window
        }
    }

    @Test
    void anImmediatePanelFollowsItsElement() throws Exception {
        try (App app = App.start()) {
            View host = redPage(app, "swaswa test: immediate panel");
            Document page = Document.of(host);
            ImmediatePanel panel = new ImmediatePanel(ui -> ui.label("a component"));
            panel.setPreferredSize(300, 200).setMargin("40px 0 0 20px");
            Div spacer = Html.div().setPreferredSize(10, 0);
            page.body().add(spacer, panel);
            page.flush().join();
            ImmediateWindow ui = until(panel::getPanel, java.util.Objects::nonNull);
            assertNotNull(ui, "made once its element is in the page");
            assertNotNull(widget(ui, "a component"));
            ViewMethodsTest.front(host);
            assertFalse(red(until(() -> at(host, 170, 140), c -> !red(c))), "where its element is");

            spacer.setPreferredSize(10, 150);   // the layout moves it down
            assertTrue(red(until(() -> at(host, 170, 60), EguiPanelTest::red)), "moved with its element: the page where it was");
            assertFalse(red(until(() -> at(host, 170, 300), c -> !red(c))), "and the panel where its element went");

            panel.setVisible(false);
            assertTrue(red(until(() -> at(host, 170, 300), EguiPanelTest::red)), "hidden with its element");
            panel.setVisible(true);
            assertFalse(red(until(() -> at(host, 170, 300), c -> !red(c))), "shown again");

            page.body().remove(panel);
            ui.whenClosed().get(10, TimeUnit.SECONDS);   // taken out of the page: closed
            assertNull(panel.getPanel());
        }
    }
}
