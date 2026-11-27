package io.joshuasalcedo.swaswa.ffi;

import static io.joshuasalcedo.swaswa.ffi.ViewMethodsTest.until;
import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** The showcase's workspace, as it docks: a window let go on it is a panel in it, in view. */
@EnabledOnOs(OS.WINDOWS)
@Timeout(120)
class WorkspaceDockTest {

    @Test
    void aWindowLetGoOnTheWorkspaceIsInItInView() throws Exception {
        try (App app = App.start()) {
            View host = app.open(WindowOptions.html("<body style='margin:0;background:#dde'>workspace").title("swaswa test: workspace").size(1100, 720).position(120, 80)).join();
            View page = DockTest.stateful(app, "swaswa test: to dock", 1300);
            page.eval("document.documentElement.style.background = document.body.style.background = 'rgb(220,30,30)'").join();
            AtomicReference<Docking.Drop> dropped = new AtomicReference<>();
            Docking docking = Docking.on(app).hosts(v -> v.equals(host)).dockable(v -> !v.equals(host)).grow(false)
                    .onDock(drop -> {
                        dropped.set(drop);
                        drop.host().adopt(drop.dragged(), drop.x(), drop.y(), drop.width(), drop.height()).join();
                    });
            page.focus().join();
            docking.moved(DockingTest.over(page, host, WindowMove.Phase.MOVING, 550, 360, false));
            docking.moved(DockingTest.over(page, host, WindowMove.Phase.ENDED, 550, 360, false));
            Docking.Drop d = until(dropped::get, java.util.Objects::nonNull);
            System.out.println("workspace drop: " + d.zone() + " " + d.x() + "," + d.y() + " " + d.width() + "x" + d.height());
            assertTrue(d.width() > 50 && d.height() > 50, "a place of some size: " + d);
            double w = until(() -> DockTest.innerWidth(page), x -> Math.abs(x - d.width()) < 3);
            assertEquals(d.width(), w, 3, "in the workspace at the drop's size");
            DockTest.stillTheSamePage(page);
            // and on the screen: the page is what shows in the workspace, not the workspace's own page
            ViewMethodsTest.front(host);
            java.util.Map<?, ?> client = (java.util.Map<?, ?>) host.client().join();
            int cx = ((Number) client.get("x")).intValue() + ((Number) client.get("width")).intValue() / 2;
            int cy = ((Number) client.get("y")).intValue() + ((Number) client.get("height")).intValue() / 2;
            long red = 0x1E1EDCL;   // rgb(220,30,30) as 0x00BBGGRR
            long seen = until(() -> Win32.pixelOnScreen(cx, cy), c -> Win32.near(c, red));
            assertTrue(Win32.near(seen, red), "the docked page shows in the workspace: 0x" + Long.toHexString(seen));
        }
    }
}
