package io.joshuasalcedo.swaswa.html;

import static org.junit.jupiter.api.Assertions.*;

import io.joshuasalcedo.swaswa.component.Component;
import io.joshuasalcedo.swaswa.component.Event;
import io.joshuasalcedo.swaswa.ffi.App;
import io.joshuasalcedo.swaswa.ffi.View;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** A draggable component let go on a drop target goes in: in Java and in the page; dragged over, the target shows it. */
@EnabledOnOs(OS.WINDOWS)
@Timeout(60)
class DragDropTest {

    @Test
    void aDraggableComponentLetGoOnADropTargetGoesIn() throws Exception {
        try (App app = App.start()) {
            View view = DocumentTest.page(app, "swaswa test: drag and drop");
            Document doc = Document.of(view);
            Span card = Html.span("a card").setId("card").setDraggable(true);
            Div from = Html.div(card).setId("from");
            Div to = Html.div(Html.p("drop here")).setId("to").setDropTarget(true);
            AtomicReference<Event> dropped = new AtomicReference<>();
            to.addDropListener(dropped::set);
            doc.body().add(from, to);
            doc.flush().join();
            assertEquals("grab", DocumentTest.js(view, "getComputedStyle(document.getElementById('card')).cursor"), "it shows it can be dragged");

            // a drag, as the browser makes it: picked up, over the target, let go
            DocumentTest.js(view, """
                    window.__dt = new DataTransfer();
                    document.getElementById('card').dispatchEvent(new DragEvent('dragstart', {bubbles: true, dataTransfer: __dt}));
                    document.getElementById('to').dispatchEvent(new DragEvent('dragover', {bubbles: true, cancelable: true, dataTransfer: __dt}));
                    """);
            assertEquals(Boolean.TRUE, DocumentTest.js(view, "document.getElementById('to').hasAttribute('data-swaswa-drop-over')"),
                    "dragged over: it shows so");
            DocumentTest.js(view, "document.getElementById('to').dispatchEvent(new DragEvent('drop', {bubbles: true, cancelable: true, dataTransfer: __dt}))");

            Event drop = DocumentTest.until(dropped::get, java.util.Objects::nonNull);
            assertNotNull(drop, "its drop listener told");
            assertSame(card, drop.dragged(), "what was dragged: the component itself");
            assertSame(to, ((Component<?>) card).getParent(), "moved in, in Java");
            doc.flush().join();
            assertEquals("to", DocumentTest.until(() -> DocumentTest.js(view, "document.getElementById('card')?.parentElement?.id"), "to"::equals),
                    "and in the page");
            assertEquals(Boolean.FALSE, DocumentTest.js(view, "document.getElementById('to').hasAttribute('data-swaswa-drop-over')"));
        }
    }

    @Test
    void notADropTargetTakesNothingIn() throws Exception {
        try (App app = App.start()) {
            View view = DocumentTest.page(app, "swaswa test: no drop");
            Document doc = Document.of(view);
            Span card = Html.span("a card").setId("card").setDraggable(true);
            Div from = Html.div(card).setId("from");
            Div to = Html.div(Html.p("not a target")).setId("to");
            doc.body().add(from, to);
            doc.flush().join();
            DocumentTest.js(view, """
                    const dt = new DataTransfer();
                    document.getElementById('card').dispatchEvent(new DragEvent('dragstart', {bubbles: true, dataTransfer: dt}));
                    document.getElementById('to').dispatchEvent(new DragEvent('drop', {bubbles: true, cancelable: true, dataTransfer: dt}));
                    """);
            Thread.sleep(300);
            assertSame(from, ((Component<?>) card).getParent(), "it stays where it was");
        }
    }
}
