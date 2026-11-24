package io.joshuasalcedo.webview4j.spring;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;

/** {@code webview4j.client-url}: where every HTTP service client of a desktop client sends its calls. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(RestClientHttpServiceGroupConfigurer.class)
@ConditionalOnProperty("webview4j.client-url")
class ClientUrlConfiguration {

    @Bean
    RestClientHttpServiceGroupConfigurer webview4jClientUrl(WebView4jProperties properties) {
        return groups -> groups.forEachClient((group, builder) -> builder.baseUrl(properties.getClientUrl()));
    }
}
