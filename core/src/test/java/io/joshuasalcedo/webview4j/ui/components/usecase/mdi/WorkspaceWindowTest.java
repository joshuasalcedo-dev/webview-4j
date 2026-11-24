package io.joshuasalcedo.webview4j.ui.components.usecase.mdi;

import static io.joshuasalcedo.webview4j.ui.Ui.*;
import static org.junit.jupiter.api.Assertions.*;

import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.Geometry;
import io.joshuasalcedo.webview4j.Monitor;
import io.joshuasalcedo.webview4j.View;
import io.joshuasalcedo.webview4j.WindowOptions;
import io.joshuasalcedo.webview4j.ui.Component;
import io.joshuasalcedo.webview4j.ui.Screen;
import io.joshuasalcedo.webview4j.ui.Ui;
import io.joshuasalcedo.webview4j.ui.UiWindow;
import io.joshuasalcedo.webview4j.ui.WindowMemory;
import io.joshuasalcedo.webview4j.ui.components.layout.GridPane;
import io.joshuasalcedo.webview4j.ui.components.layout.SplitPane;
import io.joshuasalcedo.webview4j.ui.components.usecase.collection.DataTable;
import io.joshuasalcedo.webview4j.ui.state.Codec;
import io.joshuasalcedo.webview4j.ui.state.Saved;
import io.joshuasalcedo.webview4j.ui.state.StateStorage;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** A workspace: any component in an MDI window, its layout kept and put back; a window and its monitors remembered. */
@EnabledOnOs(OS.WINDOWS)
class WorkspaceWindowTest {

    record Order(String id, String customer) {}

    static final class Workspace implements Screen {
        final AtomicReference<MdiLayout> told = new AtomicReference<>();

        @Override
        public Component<?> render() {
            return new Mdi("Workspace",
                    new MdiWindow("orders", "Orders", DataTable.of("Orders", List.of(new Order("1", "Grace Hopper")))).bounds(10, 10, 360, 200),
                    new MdiWindow("form", "Customer", new GridPane().hgap(8).addRow(0, text("Name"), textField("Name"))).bounds(40, 40, 360, 200),
                    new MdiWindow("split", "Editor", new SplitPane(text("Outline"), text("Text")).height(150)).bounds(70, 70, 360, 200))
                    .remember("workspace").arrangeButtons().onLayoutChange(told::set).height(560);
        }
    }

    static String js(View view, String script) {
        return String.valueOf(view.eval(script).join());
    }

    static void until(java.util.function.BooleanSupplier done, String what) throws InterruptedException {
        for (int i = 0; i < 100 && !done.getAsBoolean(); i++) Thread.sleep(100);
        assertTrue(done.getAsBoolean(), what);
    }

    @Test
    @Timeout(120)
    void anyComponentInAWindowAndTheLayoutComesBack() throws Exception {
        StateStorage disk = StateStorage.memory();
        String tiled;
        try (App app = App.start()) {
            UiWindow.session(app).storage(disk);
            Workspace screen = new Workspace();
            View view = Ui.open(app, screen, o -> o.headless(true).size(1000, 700)).join().view();
            until(() -> js(view, "document.body.innerText").contains("Grace Hopper"), "a table in a window");
            String text = js(view, "document.body.innerText");
            assertTrue(text.contains("Outline") && text.contains("Name"), "a split pane and a form grid in windows: " + text);

            view.eval("[...document.querySelectorAll('.nt-mdi-arrange button')].find(b => b.innerText === 'Tile').click()").join();
            until(() -> screen.told.get() != null && screen.told.get().of("orders").map(p -> p.x() == 0 || p.width() > 360).orElse(false),
                    "Java hears the tiled layout");
            tiled = js(view, "JSON.stringify([...document.querySelectorAll('.nt-mdi-window')].map(w => [Math.round(w.offsetLeft), Math.round(w.offsetTop), Math.round(w.offsetWidth)]))");
            Thread.sleep(300);
            UiWindow.session(app).flush();
        }
        // the next run: the same disk, the windows where they were left
        try (App app = App.start()) {
            UiWindow.session(app).storage(disk);
            View view = Ui.open(app, new Workspace(), o -> o.headless(true).size(1000, 700)).join().view();
            until(() -> js(view, "document.body.innerText").contains("Grace Hopper"), "drawn again");
            String again = "";
            for (int i = 0; i < 30 && !again.equals(tiled); i++) {
                Thread.sleep(100);
                again = js(view, "JSON.stringify([...document.querySelectorAll('.nt-mdi-window')].map(w => [Math.round(w.offsetLeft), Math.round(w.offsetTop), Math.round(w.offsetWidth)]))");
            }
            assertEquals(tiled, again, "tiled, as left");
        }
    }

    @Test
    @Timeout(120)
    void aWindowOpensWhereItWasLeftAndOnAMonitorThatIsThere() throws Exception {
        StateStorage disk = StateStorage.memory();
        Geometry left;
        try (App app = App.start()) {
            UiWindow.session(app).storage(disk);
            View view = app.open(WindowOptions.html("<p>main</p>").headless(true).size(640, 480).position(120, 140).remember("main")).join();
            List<Monitor> monitors = view.monitors().join();
            assertFalse(monitors.isEmpty());
            assertEquals(1, monitors.stream().filter(Monitor::primary).count(), "one main monitor: " + monitors);

            view.setBounds(200, 160, 700, 500).join();
            Thread.sleep(900);   // kept once the move settles
            left = view.geometry().join();
            UiWindow.session(app).flush();
        }
        try (App app = App.start()) {
            UiWindow.session(app).storage(disk);
            View view = app.open(WindowOptions.html("<p>main</p>").headless(true).size(640, 480).remember("main")).join();
            Geometry g = view.geometry().join();
            // where it was left: as the window reported it (its inner size; setBounds set the outer one)
            assertEquals(left.x(), g.x(), 2);
            assertEquals(left.y(), g.y(), 2);
            assertEquals(left.width(), g.width(), 2);
            assertEquals(left.height(), g.height(), 2);
            assertTrue(Math.abs(g.x() - 200) < 20 && Math.abs(g.y() - 160) < 20, "where it was moved: " + g);

            // kept somewhere no monitor is now (one unplugged): back on the main one
            UiWindow.session(app).call(() -> {
                Saved.of("window.far", Codec.of(WindowMemory.Place.class), () -> null).set(new WindowMemory.Place(-30000, -30000, 640, 480, false));
                return null;
            });
            View far = app.open(WindowOptions.html("<p>far</p>").headless(true).size(640, 480).remember("far")).join();
            Thread.sleep(300);
            Geometry back = far.geometry().join();
            Monitor main = far.monitors().join().stream().filter(Monitor::primary).findFirst().orElseThrow();
            assertTrue(main.covers(back.x(), back.y(), back.width(), back.height()) > 0.9, "on the main monitor: " + back + " " + main);
        }
    }
}
