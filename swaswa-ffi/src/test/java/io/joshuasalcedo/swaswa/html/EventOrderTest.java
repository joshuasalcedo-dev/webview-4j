package io.joshuasalcedo.swaswa.html;

import static org.junit.jupiter.api.Assertions.*;

import io.joshuasalcedo.swaswa.ffi.App;
import io.joshuasalcedo.swaswa.ffi.View;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** A page's events reach Java in the page's order, one at a time, however fast they come; a select's choice is known. */
@EnabledOnOs(OS.WINDOWS)
@Timeout(60)
class EventOrderTest {

    @Test
    void eventsComeInThePagesOrder() throws Exception {
        try (App app = App.start()) {
            View view = DocumentTest.page(app, "swaswa test: event order");
            Document doc = Document.of(view);
            List<String> keys = new CopyOnWriteArrayList<>();
            java.util.concurrent.atomic.AtomicInteger at = new java.util.concurrent.atomic.AtomicInteger(), most = new java.util.concurrent.atomic.AtomicInteger();
            Input field = Html.input().setId("field");
            field.addKeyListener(e -> {
                most.accumulateAndGet(at.incrementAndGet(), Math::max);
                keys.add(e.key());
                at.decrementAndGet();
            });
            doc.body().add(field);
            doc.flush().join();
            DocumentTest.js(view, "const f = document.getElementById('field');"
                    + " for (let i = 0; i < 200; i++) f.dispatchEvent(new KeyboardEvent('keydown', {key: 'k' + i, bubbles: true}))");
            DocumentTest.until(keys::size, n -> n == 200);
            assertEquals(IntStream.range(0, 200).mapToObj(i -> "k" + i).toList(), keys, "in the page's order");
            assertEquals(1, most.get(), "one at a time");
        }
    }

    @Test
    void aSelectsChoiceIsKnownWithNoListenerOfTheApps() throws Exception {
        try (App app = App.start()) {
            View view = DocumentTest.page(app, "swaswa test: select");
            Document doc = Document.of(view);
            Select theme = Html.select(Html.option("SYSTEM"), Html.option("DARK")).setId("theme");
            doc.body().add(theme);
            doc.flush().join();
            DocumentTest.js(view, "const s = document.getElementById('theme'); s.value = 'DARK'; s.dispatchEvent(new Event('change', {bubbles: true}))");
            assertEquals("DARK", DocumentTest.until(theme::getValue, "DARK"::equals));
        }
    }
}
