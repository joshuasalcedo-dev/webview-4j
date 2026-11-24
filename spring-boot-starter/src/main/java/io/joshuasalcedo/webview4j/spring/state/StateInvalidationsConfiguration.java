package io.joshuasalcedo.webview4j.spring.state;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * On a Spring MVC server: the invalidations endpoint ({@link StateInvalidations}), for the desktop clients to listen
 * to. Off with {@code webview4j.invalidations-endpoint=false}. On whether or not a window opens (a server with no
 * window of its own still tells its clients).
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "org.springframework.web.servlet.mvc.method.annotation.SseEmitter")
@ConditionalOnProperty(name = "webview4j.invalidations-endpoint", havingValue = "true", matchIfMissing = true)
public class StateInvalidationsConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public StateInvalidations stateInvalidations() {
        return new StateInvalidations();
    }
}
