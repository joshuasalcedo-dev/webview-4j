package io.joshuasalcedo.swaswa.ffi;

import static io.joshuasalcedo.swaswa.ffi.ViewMethodsTest.until;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** A window's MDI children laid out at once: cascade, grid, rows, columns. */
@Timeout(120)
class MdiLayoutTest {

    @Test
    void cascadeStepsEachBelowAndRightOfTheLast() {
        List<double[]> p = MdiLayout.CASCADE.places(3, 0, 0, 1000, 600);
        assertArrayEquals(new double[] {0, 0, 600, 360}, p.get(0));
        assertArrayEquals(new double[] {30, 30, 600, 360}, p.get(1));
        assertArrayEquals(new double[] {60, 60, 600, 360}, p.get(2));
        for (double[] b : MdiLayout.CASCADE.places(40, 0, 0, 1000, 600)) {
            assertTrue(b[1] + b[3] <= 600, "wrapped, within the area: " + b[0] + "," + b[1]);
        }
    }

    @Test
    void aGridIsAsSquareAsItsNumberAllowsAndItsLastRowSharesTheWidth() {
        List<double[]> p = MdiLayout.GRID.places(5, 0, 0, 900, 600);   // 3 columns, 2 rows: 3 then 2
        assertArrayEquals(new double[] {0, 0, 300, 300}, p.get(0));
        assertArrayEquals(new double[] {600, 0, 300, 300}, p.get(2));
        assertArrayEquals(new double[] {0, 300, 450, 300}, p.get(3), "the last row: two, half the width each");
        assertArrayEquals(new double[] {450, 300, 450, 300}, p.get(4));
        assertEquals(4, MdiLayout.GRID.places(4, 0, 0, 800, 600).stream().filter(b -> b[2] == 400 && b[3] == 300).count(), "4: two by two");
    }

    @Test
    void rowsAndColumnsShareTheArea() {
        assertArrayEquals(new double[] {10, 220, 900, 200}, MdiLayout.ROWS.places(3, 10, 20, 900, 600).get(1));
        assertArrayEquals(new double[] {600, 0, 200, 500}, MdiLayout.COLUMNS.places(4, 0, 0, 800, 500).get(3));
        assertTrue(MdiLayout.GRID.places(0, 0, 0, 10, 10).isEmpty());
    }

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void aWindowsChildrenAreLaidOutWhereTheLayoutSays() throws Exception {
        try (App app = App.start()) {
            View host = app.open(WindowOptions.html("<body style='margin:0'>").title("swaswa test: arrange").size(800, 600).position(200, 100)).join();
            List<View> children = new java.util.ArrayList<>();
            for (int i = 0; i < 4; i++) children.add(host.embed(WindowOptions.html("<p>" + i).draggable(true), 10 + i * 5, 10, 200, 150).join());
            host.arrange(MdiLayout.GRID, children).join();
            for (View c : children) assertEquals(400, until(() -> DockTest.innerWidth(c), x -> Math.abs(x - 400) < 3), 3, "two by two over 800");
            host.arrange(MdiLayout.COLUMNS, children).join();
            assertEquals(200, until(() -> DockTest.innerWidth(children.get(0)), x -> Math.abs(x - 200) < 3), 3, "columns: 800 / 4");
        }
    }
}
