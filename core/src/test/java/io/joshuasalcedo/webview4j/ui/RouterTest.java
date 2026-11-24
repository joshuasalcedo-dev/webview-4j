package io.joshuasalcedo.webview4j.ui;

import static io.joshuasalcedo.webview4j.ui.Ui.*;

import io.joshuasalcedo.webview4j.ui.components.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/** Pages, slugs, the query, layouts, navigation, errors and loading: the Router's tree, without a window. */
class RouterTest {

    /** Every text in a tree, in order: what a person would read. */
    @SuppressWarnings("unchecked")
    private static List<String> texts(Map<String, Object> node) {
        List<String> found = new ArrayList<>();
        Map<String, Object> p = (Map<String, Object>) node.get("p");
        for (String key : List.of("text", "title", "label")) if (p.get(key) instanceof String s) found.add(s);
        if (node.get("c") instanceof List<?> children) for (Object c : children) found.addAll(texts((Map<String, Object>) c));
        return found;
    }

    /** Presses what is labelled {@code label}, as a person would: runs its onClick or onSelect. */
    @SuppressWarnings("unchecked")
    private static boolean press(Map<String, Object> node, Map<String, Consumer<Object>> handlers, String label) {
        Map<String, Object> p = (Map<String, Object>) node.get("p");
        if (label.equals(p.get("label")) || label.equals(p.get("text"))) {
            Object id = p.containsKey("onClick") ? p.get("onClick") : p.get("onSelect");
            if (id != null) {
                handlers.get(id).accept(null);
                return true;
            }
        }
        if (node.get("c") instanceof List<?> children) {
            for (Object c : children) if (press((Map<String, Object>) c, handlers, label)) return true;
        }
        return false;
    }

    private static Map<String, Object> tree(Router router, Map<String, Consumer<Object>> handlers) {
        handlers.clear();
        return router.render().toTree(handlers);
    }

    private static List<String> show(Router router) {
        return texts(tree(router, new LinkedHashMap<>()));
    }

    @Test
    void pathsFindTheirPagesWithTheirSlugs() {
        Router router = new Router().title("Shop")
                .page(new Router.Entry("/", "Home", "Shop", 0), page -> text("home"))
                .page(new Router.Entry("/orders", "Orders", "Shop", 1), page -> text("orders page " + page.queryInt("page", 1)))
                .page("/orders/{id}", page -> text("order " + page.param("id")))
                .page("/orders/new", page -> text("a new order"))
                .page("/docs/{*rest}", page -> text("docs at " + page.param("rest")));

        assertTrue(show(router).contains("home"));
        assertTrue(show(router.start("/orders/42")).contains("order 42"));
        assertTrue(show(router.start("/orders/new")).contains("a new order"), "a fixed segment wins over a slug");
        assertTrue(show(router.start("/orders?page=3")).contains("orders page 3"));
        assertTrue(show(router.start("/docs/guide/install")).contains("docs at guide/install"));
        assertTrue(show(router.start("/orders/a%20b")).contains("order a b"), "slugs are decoded");
        assertTrue(show(router.start("/nowhere")).contains("Not found"));
        assertThrows(IllegalStateException.class, () -> router.page("/orders", page -> text("again")));
        assertThrows(IllegalArgumentException.class, () -> new Router.Entry("/orders/{id}", "Order", null, 0));
    }

    @Test
    void theDefaultLayoutHasTheNavigationAndBackAndForward() {
        Router router = new Router().title("Shop")
                .page(new Router.Entry("/", "Home", "Shop", 0), page -> button("Open order 7").onClick(() -> page.navigate("/orders/7")))
                .page(new Router.Entry("/orders", "Orders", "Shop", 1), page -> text("all orders"))
                .page("/orders/{id}", page -> text("order " + page.param("id")));
        Map<String, Consumer<Object>> handlers = new LinkedHashMap<>();
        Map<String, Object> shown = tree(router, handlers);
        List<String> home = texts(shown);
        assertTrue(home.containsAll(List.of("Shop", "Home", "Orders", "Back", "Forward", "Open order 7")), home.toString());

        // the handlers are the page's: the button navigates
        assertTrue(press(shown, handlers, "Open order 7"));
        assertEquals("/orders/7", router.location());
        assertTrue(show(router).contains("order 7"));
        assertTrue(show(router).contains("Orders"), "the side bar marks Orders while under /orders");

        router.back();
        assertEquals("/", router.location());
        router.forward();
        assertEquals("/orders/7", router.location());
    }

    @Test
    void layoutsNestFromTheRootIn() {
        Router router = new Router()
                .layout("/", (page, content) -> frame(column(text("root"), content)))
                .layout("/settings", (page, content) -> column(text("settings"), content))
                .page("/", page -> text("home"))
                .page("/settings/profile", page -> text("profile"));
        assertEquals(List.of("root", "settings", "profile"), show(router.start("/settings/profile")));
        assertEquals(List.of("root", "home"), show(router.start("/")), "/settings's layout is only under /settings");
    }

    @Test
    void aPagesErrorsStayInThePage() {
        Router router = new Router()
                .page("/", page -> {
                    throw new UiException("Orders are offline", "Try again in a minute.");
                })
                .page("/broken", page -> {
                    throw new IllegalStateException("null pointer somewhere");
                });
        List<String> said = show(router);
        assertTrue(said.containsAll(List.of("Orders are offline", "Try again in a minute.", "Try again")), said.toString());
        List<String> broken = show(router.start("/broken"));
        assertTrue(broken.contains("This page could not be shown"), broken.toString());
    }

    @Test
    void slowDataLoadsOffTheScreensThreadAndShowsWhenItArrives() throws Exception {
        Router router = new Router().page("/", page -> page.load("orders", () -> List.of("A-1", "A-2"))
                .<Component<?>>map(orders -> text(orders.size() + " orders"))
                .orElseGet(() -> spinner("Loading orders")));
        List<String> first = show(router);   // starts the load: it shows the spinner, never the data
        assertFalse(first.contains("2 orders"), first.toString());
        for (int i = 0; i < 50 && !show(router).contains("2 orders"); i++) Thread.sleep(20);
        assertTrue(show(router).contains("2 orders"));

        Router failing = new Router().page("/", page -> page.load("x", () -> {
            throw new UiException("The server is down", "Try later.");
        }).<Component<?>>map(v -> text("never")).orElseGet(() -> text("loading")));
        show(failing);
        for (int i = 0; i < 50 && !show(failing).contains("The server is down"); i++) Thread.sleep(20);
        assertTrue(show(failing).contains("The server is down"), "a failed load is the page's error");
    }

    @Test
    void paginationShowsThePagesAroundTheCurrentOne() {
        Map<String, Consumer<Object>> handlers = new LinkedHashMap<>();
        int[] went = {0};
        Map<String, Object> shown = ((Component<?>) pagination(6, 12, n -> went[0] = n)).toTree(handlers);
        assertEquals(List.of("Previous", "1", "…", "4", "5", "6", "7", "8", "…", "12", "Next"), texts(shown));
        assertTrue(press(shown, handlers, "Next"));
        assertEquals(7, went[0], "Next goes to 7");
    }
}
