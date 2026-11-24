package io.joshuasalcedo.webview4j.spring.client;

import static io.joshuasalcedo.webview4j.ui.Ui.*;

import io.joshuasalcedo.webview4j.ui.components.*;
import static org.junit.jupiter.api.Assertions.*;

import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.View;
import io.joshuasalcedo.webview4j.ViewInfo;
import io.joshuasalcedo.webview4j.spring.WebView4jWindow;
import io.joshuasalcedo.webview4j.ui.Component;
import io.joshuasalcedo.webview4j.ui.LayoutView;
import io.joshuasalcedo.webview4j.ui.PageContext;
import io.joshuasalcedo.webview4j.ui.PageView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/** A desktop client of a server: pages in the main window, the server's API in the developer window. */
@EnabledOnOs(OS.WINDOWS)
@DirtiesContext
@SpringBootTest(classes = ClientModeTest.Desktop.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"webview4j.enabled=true", "webview4j.headless=true", "webview4j.quit-on-close=false",
                "webview4j.dev=true", "spring.application.name=Orders desktop"})
class ClientModeTest {

    // ---- the API, shared by the server and the desktop app ----

    public record Order(long id, String customer, int quantity) {}

    public record NewOrder(@NotBlank String customer, @Min(1) int quantity) {}

    @HttpExchange("/orders")
    public interface OrdersApi {
        @GetExchange
        List<Order> orders();

        @GetExchange("/{id}")
        Order order(@PathVariable("id") long id);

        @PostExchange
        Order create(@RequestBody @Valid NewOrder order);
    }

    // ---- the server: an app as it is ----

    @SpringBootApplication(scanBasePackages = "io.joshuasalcedo.webview4j.spring.client.nothing")
    @RestController
    public static class Server implements OrdersApi {
        public List<Order> orders() {
            return List.of(new Order(1, "Ada", 2), new Order(2, "Grace", 5));
        }

        public Order order(long id) {
            return orders().stream().filter(o -> o.id() == id).findFirst().orElseThrow();
        }

        public Order create(NewOrder order) {
            return new Order(3, order.customer(), order.quantity());
        }
    }

    private static ConfigurableApplicationContext server;

    @BeforeAll
    static void startServer() {
        server = new SpringApplicationBuilder(Server.class)
                .properties("server.port=0", "webview4j.enabled=false", "spring.main.banner-mode=off").run();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) server.close();
    }

    @DynamicPropertySource
    static void serverUrl(DynamicPropertyRegistry registry) {
        registry.add("webview4j.client-url", () -> "http://localhost:" + server.getEnvironment().getProperty("local.server.port"));
    }

    // ---- the desktop app: its pages ----

    // The API found by its package (and the packages under it): no list of interfaces.
    @WebViewApplication(scanBasePackages = "io.joshuasalcedo.webview4j.spring.client.nothing",
            httpServices = "io.joshuasalcedo.webview4j.spring.client")
    @Import({OrdersPage.class, OrderPage.class, OrdersLayout.class})
    public static class Desktop {}

    @Page(value = "/", title = "Orders", section = "Shop")
    public static class OrdersPage implements PageView {
        private final OrdersApi orders;

        public OrdersPage(OrdersApi orders) {
            this.orders = orders;
        }

        @Override
        public Component<?> render(PageContext page) {
            return page.load("orders", orders::orders)
                    .<Component<?>>map(list -> list("Orders", list.stream()
                            .map(o -> listItem(o.customer()).key(o.id()).subtitle(o.quantity() + " items")
                                    .onSelect(() -> page.navigate("/orders/" + o.id())))
                            .toList()))
                    .orElseGet(() -> spinner("Loading orders"));
        }
    }

    @Page("/orders/{id}")
    public static class OrderPage implements PageView {
        private final OrdersApi orders;

        public OrderPage(OrdersApi orders) {
            this.orders = orders;
        }

        @Override
        public Component<?> render(PageContext page) {
            long id = Long.parseLong(page.param("id"));
            return page.load("order " + id, () -> orders.order(id))
                    .<Component<?>>map(o -> column(title("Order " + o.id()), text(o.customer() + " ordered " + o.quantity())))
                    .orElseGet(() -> spinner("Loading the order"));
        }
    }

    @Layout("/orders")
    public static class OrdersLayout implements LayoutView {
        @Override
        public Component<?> render(PageContext page, Component<?> content) {
            return column(text("ORDER DETAILS"), content).padding(16);
        }
    }

    @Autowired WebView4jWindow window;
    @Autowired App app;

    @Test
    @Timeout(120)
    void theMainWindowIsTheAppsPagesAndTheDeveloperWindowItsApi() throws Exception {
        View main = window.view().get(60, TimeUnit.SECONDS);

        // the list, loaded from the server through the imported client
        String text = until(main, "Grace");
        assertTrue(text.contains("Ada") && text.contains("Orders desktop"), text);

        // a click navigates to the page with the slug, inside its layout
        main.eval("[...document.querySelectorAll('li')].find(li => li.innerText.includes('Grace')).click()").join();
        String order = until(main, "Grace ordered 5");
        assertTrue(order.contains("Order 2") && order.contains("ORDER DETAILS"), order);

        // in development, the developer window: the API's endpoints
        ViewInfo developer = null;
        for (int i = 0; i < 100 && developer == null; i++) {
            developer = app.views().join().stream().filter(v -> v.title().endsWith(": developer")).findFirst().orElse(null);
            if (developer == null) Thread.sleep(100);
        }
        assertNotNull(developer, "the developer window opened");
        String api = until(app.view(developer.id()), "Create");
        assertTrue(api.contains("Orders") && api.contains("Order"), api);
    }

    /** The window's text once it contains {@code part}. */
    static String until(View view, String part) throws InterruptedException {
        String text = "";
        for (int i = 0; i < 150; i++) {
            text = String.valueOf(view.eval("document.body.innerText").join());
            if (text.contains(part)) return text;
            Thread.sleep(100);
        }
        throw new AssertionError("never showed '" + part + "': " + text);
    }
}
