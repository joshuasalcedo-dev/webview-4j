package io.joshuasalcedo.webview4j.spring.client;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.annotation.AliasFor;

/**
 * A desktop app for a server you already have: a Spring Boot app with no web server of its own, whose window is
 * made from the server's {@code @HttpExchange} interfaces. Import them with Spring's {@code @ImportHttpServices}, say
 * where the server is, and run it with {@link WebViewApplications#run}:
 * <pre>{@code
 * @WebViewApplication
 * @ImportHttpServices(basePackageClasses = UsersApi.class)
 * public class Desktop {
 *     public static void main(String[] args) {
 *         WebViewApplications.run(Desktop.class, args);
 *     }
 * }
 * }</pre>
 * <pre>
 * # application.properties
 * webview4j.client-url=https://api.example.com
 * </pre>
 *
 * <p>The window has a side bar of the interfaces and their methods; each method is a form made from its
 * parameters (a {@code @RequestBody} record becomes a field per component, checked with its Jakarta Validation
 * annotations before it is sent), and its answer is shown. Your own {@code Screen} beans join the side bar. Nothing is
 * generated: the interfaces are read when the app starts, and the calls go through Spring's own clients.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@SpringBootApplication
public @interface WebViewApplication {

    /** As {@link SpringBootApplication#scanBasePackages()}. */
    @AliasFor(annotation = SpringBootApplication.class)
    String[] scanBasePackages() default {};

    /** As {@link SpringBootApplication#scanBasePackageClasses()}. */
    @AliasFor(annotation = SpringBootApplication.class)
    Class<?>[] scanBasePackageClasses() default {};

    /** As {@link SpringBootApplication#exclude()}. */
    @AliasFor(annotation = SpringBootApplication.class)
    Class<?>[] exclude() default {};
}
