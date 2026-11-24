package io.joshuasalcedo.webview4j.spring.client;

import org.springframework.context.annotation.ImportSelector;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ClassUtils;

/** Registers {@link WebViewApplication#httpServices()} when Spring's HTTP service clients (spring-web) are there. */
class HttpServicesSelector implements ImportSelector {

    @Override
    public String[] selectImports(AnnotationMetadata metadata) {
        return ClientWindow.available() && ClassUtils.isPresent("org.springframework.web.client.RestClient", getClass().getClassLoader())
                ? new String[] {HttpServicesRegistrar.class.getName()}
                : new String[0];
    }
}
