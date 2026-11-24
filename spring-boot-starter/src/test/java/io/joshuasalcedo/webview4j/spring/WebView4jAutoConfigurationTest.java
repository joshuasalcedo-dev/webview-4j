package io.joshuasalcedo.webview4j.spring;

import static org.junit.jupiter.api.Assertions.*;

import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.View;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class WebView4jAutoConfigurationTest {

    /** An app as it is: a controller and a page, nothing about webview-4j. */
    // Each app is only itself: no scanning, so neither picks up the other's controller.
    @SpringBootApplication(scanBasePackages = "io.joshuasalcedo.webview4j.spring.nothing")
    @RestController
    static class ShopApp {
        @GetMapping(value = "/", produces = "text/html")
        String home() {
            return "<h1 id='shop'>Shop</h1>";
        }
    }

    /** An API with no page, describing itself at /openapi.json: as the office service is. */
    // Each app is only itself: no scanning, so neither picks up the other's controller.
    @SpringBootApplication(scanBasePackages = "io.joshuasalcedo.webview4j.spring.nothing")
    @RestController
    static class ApiOnlyApp {
        @GetMapping("/orders")
        java.util.List<String> orders() {
            return java.util.List.of("A-1");
        }

        @GetMapping(value = "/openapi.json", produces = "application/json")
        String description() {
            return """
                    {"openapi": "3.0.1", "info": {"title": "Orders", "version": "1"},
                     "paths": {"/orders": {"get": {"tags": ["orders"], "responses": {"200": {"description": "the orders"}}}}}}
                    """;
        }
    }

    @Test
    void whileTestsRunNoWindowOpensUnlessAsked() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(WebView4jAutoConfiguration.class))
                .run(context -> assertFalse(context.containsBean("webview4jApp"), "JUnit is on the class path"));
    }

    @Test
    void theWindowGoesToTheAppsOwnServer() {
        WebView4jProperties properties = new WebView4jProperties();
        properties.setPath("app");
        var environment = new org.springframework.mock.env.MockEnvironment()
                .withProperty("local.server.port", "52144").withProperty("server.servlet.context-path", "/shop/");
        assertEquals("http://localhost:52144/shop/app", new WebView4jWindow(null, properties).address(environment));
        properties.setUrl("https://books.example.com");
        assertEquals("https://books.example.com", new WebView4jWindow(null, properties).address(environment));
    }

    @Nested
    @EnabledOnOs(OS.WINDOWS)
    @DirtiesContext // one App per process: this app closes before the next starts
    @SpringBootTest(classes = ApiOnlyApp.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = {"webview4j.enabled=true", "webview4j.headless=true", "webview4j.quit-on-close=false",
                    "spring.application.name=Orders service"})
    class AnApiWithNoPage {

        @Autowired WebView4jWindow window;
        @Autowired App app;

        @Test
        @Timeout(90)
        void opensInTheApiExplorerFoundByItself() throws Exception {
            View view = window.view().get(60, TimeUnit.SECONDS);
            Object text = null;
            for (int i = 0; i < 150; i++) {
                text = view.eval("document.body.innerText").join();
                if (String.valueOf(text).contains("GET /orders")) break;
                Thread.sleep(100);
            }
            assertTrue(String.valueOf(text).contains("GET /orders"), "the explorer lists the app's endpoints: " + text);
            assertEquals(1, app.views().join().size(), "the explorer is the app's only window");
            assertEquals("Orders service", app.views().join().getFirst().title());
        }
    }

    @Nested
    @EnabledOnOs(OS.WINDOWS)
    @DirtiesContext // one App per process: this app closes before the next starts
    @SpringBootTest(classes = ShopApp.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = {"webview4j.enabled=true", "webview4j.headless=true", "webview4j.quit-on-close=false",
                    "spring.application.name=Shop"})
    class InARealApp {

        @Autowired WebView4jWindow window;
        @Autowired App app;

        @Test
        @Timeout(90)
        void theAppOpensInAWindowOnItsOwnServer() throws Exception {
            View view = window.view().get(60, TimeUnit.SECONDS);
            Object shop = null;
            for (int i = 0; i < 100 && shop == null; i++) {
                shop = view.eval("document.getElementById('shop')?.textContent ?? null").join();
                if (shop == null) Thread.sleep(100);
            }
            assertEquals("Shop", shop, "the page came from the app's own controller");
            assertTrue(String.valueOf(view.eval("location.href").join()).startsWith("http://localhost:"));
            assertEquals("Shop", app.views().join().getFirst().title(), "titled after spring.application.name");
        }
    }
}
