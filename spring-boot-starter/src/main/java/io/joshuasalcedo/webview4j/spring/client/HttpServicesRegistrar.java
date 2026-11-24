package io.joshuasalcedo.webview4j.spring.client;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ClassUtils;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.registry.AbstractHttpServiceRegistrar;

/**
 * {@link WebViewApplication#httpServices()}: every {@code @HttpExchange} interface in those packages and the packages
 * under them becomes a client (in Spring's own registry, as {@code @ImportHttpServices} would make it); only
 * interfaces, so a server's classes that implement them, next to them, are left alone. Without it, the application
 * class's own package.
 */
class HttpServicesRegistrar extends AbstractHttpServiceRegistrar implements ResourceLoaderAware {

    private ResourceLoader resourceLoader;

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        super.setResourceLoader(resourceLoader);
        this.resourceLoader = resourceLoader;
    }

    @Override
    protected void registerHttpServices(GroupRegistry registry, AnnotationMetadata metadata) {
        Map<String, Object> attributes = metadata.getAnnotationAttributes(WebViewApplication.class.getName());
        String[] packages = attributes == null ? new String[0] : (String[]) attributes.get("httpServices");
        if (packages == null || packages.length == 0) packages = new String[] {ClassUtils.getPackageName(metadata.getClassName())};

        String exchange = HttpExchange.class.getName();
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition definition) {
                AnnotationMetadata m = definition.getMetadata();
                return m.isInterface() && m.isIndependent() && !m.isAnnotation();
            }
        };
        if (resourceLoader != null) scanner.setResourceLoader(resourceLoader);
        scanner.addIncludeFilter((reader, factory) -> reader.getClassMetadata().isInterface()
                && (reader.getAnnotationMetadata().isAnnotated(exchange) || reader.getAnnotationMetadata().hasAnnotatedMethods(exchange)));
        Set<String> types = new LinkedHashSet<>();
        for (String base : packages) {
            for (var candidate : scanner.findCandidateComponents(base)) types.add(candidate.getBeanClassName());
        }
        if (!types.isEmpty()) registry.forDefaultGroup().registerTypeNames(types.toArray(String[]::new));
    }
}
