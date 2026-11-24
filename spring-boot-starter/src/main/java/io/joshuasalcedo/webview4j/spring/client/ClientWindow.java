package io.joshuasalcedo.webview4j.spring.client;

import io.joshuasalcedo.webview4j.App;
import io.joshuasalcedo.webview4j.View;
import io.joshuasalcedo.webview4j.ui.Ui;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.springframework.context.ApplicationContext;
import org.springframework.util.ClassUtils;
import org.springframework.web.service.registry.HttpServiceProxyRegistry;

/**
 * A {@link WebViewApplication}'s developer window: every endpoint of the HTTP service clients it imported, as a form
 * made from the method, sent for real.
 */
public final class ClientWindow {

    private ClientWindow() {}

    /** Whether Spring's HTTP service clients are on the class path (spring-web). */
    public static boolean available() {
        return ClassUtils.isPresent("org.springframework.web.service.registry.HttpServiceProxyRegistry", ClientWindow.class.getClassLoader());
    }

    public static CompletableFuture<View> open(App app, ApplicationContext context, String title, double width, double height, boolean headless) {
        Map<Class<?>, Object> clients = new LinkedHashMap<>();
        context.getBeanProvider(HttpServiceProxyRegistry.class).ifAvailable(registry -> {
            for (String group : registry.getGroupNames()) {
                for (Class<?> type : registry.getClientTypesInGroup(group)) clients.putIfAbsent(type, registry.getClient(group, type));
            }
        });

        ClientScreen screen = new ClientScreen(title, Map.of(), clients, checker(context));
        return Ui.open(app, screen, o -> o.title(title).size(width, height).minSize(720, 480).decorations(false).headless(headless))
                .thenApply(window -> {
                    screen.attach(window);
                    return window.view();
                });
    }

    /** Jakarta Validation when it is there: the app's validator bean, or one of its own. */
    private static ClientScreen.Checker checker(ApplicationContext context) {
        if (!ClassUtils.isPresent("jakarta.validation.Validator", ClientWindow.class.getClassLoader())) return null;
        jakarta.validation.Validator validator = context.getBeanProvider(jakarta.validation.Validator.class).getIfAvailable();
        if (validator == null) {
            try {
                validator = jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator();
            } catch (RuntimeException noProvider) {
                return null;   // the API without an implementation (Hibernate Validator): nothing to check with
            }
        }
        return new JakartaChecker(validator);
    }
}
