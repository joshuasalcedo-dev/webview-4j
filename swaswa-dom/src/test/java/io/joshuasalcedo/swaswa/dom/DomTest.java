package io.joshuasalcedo.swaswa.dom;

import static org.junit.jupiter.api.Assertions.*;

import io.joshuasalcedo.swaswa.component.Property;
import io.joshuasalcedo.swaswa.ffi.App;
import io.joshuasalcedo.swaswa.ffi.Theme;
import io.joshuasalcedo.swaswa.ffi.View;
import io.joshuasalcedo.swaswa.ffi.WindowOptions;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** A page from Java, typed: scripts' values, its functions behind an interface, the browser's APIs, values followed. */
@EnabledOnOs(OS.WINDOWS)
@Timeout(90)
class DomTest {

    static final String PAGE = """
            <!doctype html><title>dom test</title>
            <body style="margin:0"><p id=hello style="margin:20px;width:200px">Hello, page</p><div style="height:3000px"></div>
            <script>
              function total(items) { return items.reduce((s, i) => s + i.price * i.count, 0); }
              async function later(ms) { await new Promise(r => setTimeout(r, ms)); return 'waited ' + ms; }
              window.app = { cart: { size: () => 3 } };
              function broken() { throw new Error('no stock'); }
            </script>""";

    static <T> T until(Supplier<T> value, Predicate<T> done) throws InterruptedException {
        T v = value.get();
        for (int i = 0; i < 100 && !done.test(v); i++) {
            Thread.sleep(50);
            v = value.get();
        }
        return v;
    }

    static View page(App app, String title) {
        return app.open(WindowOptions.html(PAGE).title(title).size(700, 500)).join();
    }

    record Item(String name, double price, int count) {}

    record Totals(double sum, int count, List<String> names) {}

    interface Shop {
        double total(List<Item> items);

        CompletableFuture<String> later(int ms);

        @PageFunction("app.cart.size")
        int cartSize();

        void broken();
    }

    @Test
    void scriptsAndFunctionsAnswerTyped() throws Exception {
        try (App app = App.start()) {
            Dom page = Dom.of(page(app, "swaswa test: dom"));
            assertEquals(new Totals(12.5, 3, List.of("a", "b")), page.eval("({sum: 12.5, count: 3, names: ['a', 'b']})", Totals.class).join());
            assertEquals(42, page.eval("6 * 7", Integer.class).join());
            assertEquals(List.of(1L, 2L, 3L), page.evalList("[1, 2, 3]", Long.class).join());

            Shop shop = page.bind(Shop.class);
            assertEquals(10.5, shop.total(List.of(new Item("pen", 1.5, 3), new Item("book", 6, 1))), 1e-9, "records in, a number back");
            CompletableFuture<String> waited = shop.later(150);
            assertFalse(waited.isDone(), "a future: it does not wait");
            assertEquals("waited 150", waited.join(), "an async function's promise, settled");
            assertEquals(3, shop.cartSize(), "a function on a path");
            PageCallException failed = assertThrows(PageCallException.class, shop::broken);
            assertEquals("no stock", failed.getMessage(), "the page's error, its message");
            CompletionException missing = assertThrows(CompletionException.class, () -> page.call("nothing", List.of()).join());
            assertInstanceOf(PageCallException.class, missing.getCause());
        }
    }

    @Test
    void theBrowsersOwnAnswerTyped() throws Exception {
        try (App app = App.start()) {
            View view = page(app, "swaswa test: dom browser");
            Dom page = Dom.of(view);
            Size size = page.size().join();
            assertTrue(size.width() > 600 && size.height() > 400, "its viewport: " + size);
            assertTrue(page.pixelRatio().join() > 0);
            page.scrollTo(0, 500).join();
            assertEquals(500, until(() -> page.scroll().join().y(), y -> y == 500), 1, "scrolled");
            page.setTitle("named from Java").join();
            assertEquals("named from Java", page.title().join());
            assertEquals("about:blank", page.url().join(), "a page made in Java: about:blank");
            Rect hello = page.bounds("#hello").join().orElseThrow();
            assertEquals(200, hello.width(), 1, "the element's box");
            assertTrue(page.bounds("#nothing").join().isEmpty());
            view.eval("const r = document.createRange(); r.selectNodeContents(document.getElementById('hello'));"
                    + " getSelection().removeAllRanges(); getSelection().addRange(r)").join();
            assertEquals("Hello, page", page.selection().join());
        }
    }

    @Test
    void valuesAreFollowedAsTheyChange() throws Exception {
        try (App app = App.start()) {
            View view = page(app, "swaswa test: dom follow");
            Dom page = Dom.of(view);
            view.setTheme(Theme.LIGHT).join();
            Property<Boolean> dark = page.media("(prefers-color-scheme: dark)");
            assertFalse(until(dark::get, d -> !d));
            view.setTheme(Theme.DARK).join();
            assertTrue(until(dark::get, d -> d), "the window dark: the query holds, Java told");
            assertSame(dark, page.media("(prefers-color-scheme: dark)"), "one property per query");

            view.reload().join();
            Thread.sleep(800);
            view.setTheme(Theme.LIGHT).join();
            assertFalse(until(dark::get, d -> !d), "still followed after the page loaded again");

            assertTrue(page.online().get());
            Property<Boolean> visible = page.visible();
            assertTrue(visible.get());
            view.minimize().join();
            assertFalse(until(visible::get, v -> !v), "minimized: not seen");
            view.restore().join();
            assertTrue(until(visible::get, v -> v), "restored: seen");
        }
    }

    @Test
    void theClipboard() throws Exception {
        try (App app = App.start()) {
            View view = page(app, "swaswa test: dom clipboard");
            Dom page = Dom.of(view);
            view.focus().join();
            String text = "from swaswa " + System.nanoTime();
            page.clipboard().writeText(text).join();
            assertEquals(text, page.clipboard().readText().join(), "written and read back");
        }
    }
}
