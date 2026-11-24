package io.joshuasalcedo.webview4j.spring;

import io.joshuasalcedo.webview4j.App;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.util.ClassUtils;

/**
 * webview-4j for a Spring Boot app: its event loop ({@link App}, a bean to inject for dialogs, sounds and more
 * windows) and its window ({@link WebView4jWindow}), opened on the app's own server once it is ready.
 */
@AutoConfiguration
@Conditional(WebView4jAutoConfiguration.Enabled.class)
@EnableConfigurationProperties(WebView4jProperties.class)
public class WebView4jAutoConfiguration {

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public App webview4jApp() {
        return App.start();
    }

    @Bean
    @ConditionalOnMissingBean
    public WebView4jWindow webview4jWindow(App app, WebView4jProperties properties) {
        return new WebView4jWindow(app, properties);
    }

    /** {@code webview4j.enabled} when set; otherwise on, unless tests run (JUnit on the class path). */
    static final class Enabled implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            String enabled = context.getEnvironment().getProperty("webview4j.enabled");
            if (enabled != null) return Boolean.parseBoolean(enabled);
            return !ClassUtils.isPresent("org.junit.jupiter.api.Test", context.getClassLoader())
                    && !ClassUtils.isPresent("org.junit.Test", context.getClassLoader());
        }
    }
}
