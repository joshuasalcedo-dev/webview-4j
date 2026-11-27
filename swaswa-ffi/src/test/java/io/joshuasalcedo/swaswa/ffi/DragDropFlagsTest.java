package io.joshuasalcedo.swaswa.ffi;

import static io.joshuasalcedo.swaswa.ffi.ViewMethodsTest.until;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** Dragging and dropping by flags: off by default; a drop target takes in what is draggable and nothing else; an embedded draggable is an MDI child. */
@EnabledOnOs(OS.WINDOWS)
@Timeout(120)
class DragDropFlagsTest {

    static boolean embedded(View view) {
        try {
            view.geometry().join();
            return false;
        } catch (RuntimeException e) {
            return true;
        }
    }

    @Test
    void offByDefault() throws Exception {
        try (App app = App.start()) {
            View plain = app.open(WindowOptions.html("<p>p").title("swaswa test: plain")).join();
            assertFalse(plain.isDropTarget());
            assertFalse(plain.isDraggable());
        }
    }

    @Test
    void aDropTargetTakesInWhatIsDraggableAndNothingElse() throws Exception {
        try (App app = App.start()) {
            View host = app.open(WindowOptions.html("<body style='margin:0;background:#dde'>").title("swaswa test: flag host")
                    .size(900, 600).position(300, 100).dropTarget(true)).join();
            View plain = app.open(WindowOptions.html("<p>plain").title("swaswa test: not draggable").size(300, 200).position(1300, 100)).join();
            View draggable = app.open(WindowOptions.html("<p>in").title("swaswa test: draggable").size(300, 200).position(1300, 400).draggable(true)).join();
            assertTrue(host.isDropTarget());
            assertTrue(draggable.isDraggable());
            Docking docking = app.docking();

            docking.moved(DockingTest.over(plain, host, WindowMove.Phase.MOVING, 450, 300, false));
            docking.moved(DockingTest.over(plain, host, WindowMove.Phase.ENDED, 450, 300, false));
            Thread.sleep(500);
            assertFalse(embedded(plain), "not draggable: still a window");

            ViewMethodsTest.front(host);   // the drop target in front where it is let go (not behind another app's window)
            draggable.focus().join();
            docking.moved(DockingTest.over(draggable, host, WindowMove.Phase.MOVING, 40, 300, false));
            docking.moved(DockingTest.over(draggable, host, WindowMove.Phase.ENDED, 40, 300, false));
            assertTrue(until(() -> embedded(draggable), Boolean.TRUE::equals), "draggable: taken in, an embedded view of the host now");

            host.setDropTarget(false);
            assertFalse(host.isDropTarget(), "and off again at run time");
        }
    }

    @Test
    void anEmbeddedDraggableIsAnMdiChild() throws Exception {
        try (App app = App.start()) {
            View host = app.open(WindowOptions.html("<body style='margin:0'>").title("swaswa test: mdi flag").size(700, 500)).join();
            View child = host.embed(WindowOptions.html("<p>c").draggable(true), 20, 20, 200, 150).join();
            assertTrue(child.isDraggable());
            child.toFront().join();
            child.setDraggable(false).join();
            assertFalse(child.isDraggable());
        }
    }
}
