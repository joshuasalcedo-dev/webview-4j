package io.joshuasalcedo.webview4j.spring.client;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

/** Runs a {@link WebViewApplication}. */
public final class WebViewApplications {

    private WebViewApplications() {}

    /**
     * Starts the app as a desktop client: Spring without a web server of its own, and its window. The process lives
     * as long as the window does; closing it stops the app.
     */
    public static ConfigurableApplicationContext run(Class<?> application, String... args) {
        SpringApplication spring = new SpringApplication(application);
        spring.setWebApplicationType(WebApplicationType.NONE);
        return spring.run(args);
    }
}
