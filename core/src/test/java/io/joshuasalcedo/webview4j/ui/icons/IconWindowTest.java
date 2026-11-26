package io.joshuasalcedo.webview4j.ui.icons;

import static org.junit.jupiter.api.Assertions.*;

import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.View;
import io.joshuasalcedo.webview4j.WindowOptions;
import io.joshuasalcedo.webview4j.ui.Style;
import io.joshuasalcedo.webview4j.ui.Window;
import io.joshuasalcedo.webview4j.ui.components.DropdownMenu;
import io.joshuasalcedo.webview4j.ui.components.Text;
import io.joshuasalcedo.webview4j.ui.components.TextVariant;
import io.joshuasalcedo.webview4j.ui.components.layout.HBox;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** Lucide's icons from Java's typed Icon: on their own, and on a component. */
@EnabledOnOs(OS.WINDOWS)
class IconWindowTest {

    @Test
    void everyIconHasAKey() {
        assertEquals("lucide:shopping-cart", Icon.SHOPPING_CART.key());
        assertTrue(Icon.values().length > 1500, "Lucide's whole set");
    }

    @Test
    @Timeout(90)
    void iconsDrawAsLucidesSvgs() throws Exception {
        String out = System.getProperty("demo.out");
        Window window = new Window("Icons") {
            {
                setSize(640, 300);
                add(new Text("Lucide, typed in Java").variant(TextVariant.TITLE),
                        new HBox(14,
                                new IconView(Icon.HOUSE).size(28),
                                new IconView(Icon.SHOPPING_CART).size(28).color(Style.Token.ACCENT).label("Cart"),
                                new IconView(Icon.STAR).size(28),
                                new IconView(Icon.BELL).size(28),
                                new IconView(Icon.CALENDAR).size(28),
                                new IconView(Icon.SETTINGS).size(28),
                                new IconView(Icon.TRASH_2).size(28),
                                new IconView(Icon.CHART_LINE).size(28).color(Style.Token.ACCENT)),
                        new DropdownMenu("Sort").icon(Icon.ARROW_DOWN_WIDE_NARROW).item("Name", () -> {}));
            }

            @Override
            protected WindowOptions options(WindowOptions o) {
                return super.options(o).headless(out == null);
            }
        };
        try (App app = App.start()) {
            View view = window.open(app).join().view();
            String count = "document.querySelectorAll('svg.lucide').length";
            for (int i = 0; i < 150 && Double.parseDouble(String.valueOf(view.eval(count).join())) < 9; i++) Thread.sleep(100);
            assertEquals(9, (int) Double.parseDouble(String.valueOf(view.eval(count).join())), "eight icons and the menu's");
            assertEquals("true", String.valueOf(view.eval("!!document.querySelector('svg.lucide-shopping-cart')").join()));
            if (out != null) {
                Thread.sleep(400);
                view.screenshot(Path.of(out).resolve("icons.png")).join();
            }
        }
    }
}
