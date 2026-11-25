package io.joshuasalcedo.webview4j.spring;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * What the dev logger shows from the app beyond webview-4j's own events: its Logback lines, and the calls its
 * RestClient, RestTemplate (and Spring's HTTP service clients) and WebClient make. Each only when the app has it;
 * all only when the dev logger is wanted (webview4j.dev-logger; unset: in development, with a window to show).
 */
@AutoConfiguration(before = WebView4jAutoConfiguration.class)
@Conditional(WebView4jAutoConfiguration.Enabled.class)
@EnableConfigurationProperties(WebView4jProperties.class)
public class DevLogConfiguration {

    @Bean
    public DevLogSink webview4jDevLogSink(WebView4jProperties properties, Environment environment, ConfigurableApplicationContext context) {
        boolean wanted = properties.getDevLogger() != null ? properties.getDevLogger()
                : WebView4jWindow.development(environment, context);
        return new DevLogSink(wanted && !properties.isHeadless());
    }

    /** Logback (Spring Boot's logging): its lines from now on, the start's too (they wait for the window). */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "ch.qos.logback.classic.LoggerContext")
    static class Logback implements org.springframework.beans.factory.DisposableBean {
        Logback(DevLogSink sink) {
            if (sink.on()) DevLogAppender.install(sink);
        }

        /** The context closed: its appender goes (a context started again installs its own). */
        @Override
        public void destroy() {
            DevLogAppender.uninstall();
        }
    }

    /** RestClient (and the HTTP service clients Spring makes from it). */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.boot.restclient.RestClientCustomizer")
    static class RestClients {
        @Bean
        org.springframework.boot.restclient.RestClientCustomizer webview4jDevLogRestClient(DevLogSink sink) {
            DevLogHttp calls = new DevLogHttp(sink);
            return builder -> { if (sink.on()) builder.requestInterceptor(calls); };
        }
    }

    /**
     * RestTemplate: deprecated in Spring 7.1, still in apps. Its own class, loaded only while RestTemplate's
     * customizer exists, so the starter goes on working once it is gone.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.boot.restclient.RestTemplateCustomizer")
    @SuppressWarnings("removal")
    static class RestTemplates {
        @Bean
        org.springframework.boot.restclient.RestTemplateCustomizer webview4jDevLogRestTemplate(DevLogSink sink) {
            DevLogHttp calls = new DevLogHttp(sink);
            return template -> { if (sink.on()) template.getInterceptors().add(calls); };
        }
    }

    /** WebClient (status, time and headers; its bodies are streams). */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.boot.webclient.WebClientCustomizer")
    static class WebClients {
        @Bean
        org.springframework.boot.webclient.WebClientCustomizer webview4jDevLogWebClient(DevLogSink sink) {
            DevLogWebClient calls = new DevLogWebClient(sink);
            return builder -> { if (sink.on()) builder.filter(calls); };
        }
    }
}
